const fetch = require("node-fetch");
const fs = require("fs");
const { Client } = require("@opensearch-project/opensearch");
const { Pool } = require("pg");
const { MongoClient } = require("mongodb");
const argv = require("minimist")(process.argv.slice(2));
const configPath = argv.config || "/srv/vertx/entcore/conf/entcore.conf";
const configFile = JSON.parse(fs.readFileSync(configPath).toString());
const serviceExplorer = configFile.services.filter((service) => {
  return (service.name || "").startsWith("com.opendigitaleducation~explorer");
})[0];
const serviceMongo = configFile.services.filter((service) => {
  return (service.name || "").startsWith("io.vertx~mod-mongo-persistor");
})[0];
const servicePg = configFile.services.filter((service) => {
  return (service.name || "").startsWith("fr.wseduc~mod-postgresql");
})[0];
const esUsername = serviceExplorer.config.elasticsearchConfig.user;
const esPassword = serviceExplorer.config.elasticsearchConfig.password;
const esUri = serviceExplorer.config.elasticsearchConfig.uris[0];
const mongoHost = `mongodb://${serviceMongo.config.seeds[0].host}`;
const mongoUsername = serviceMongo.config.username;
const mongoPassword = serviceMongo.config.password;
const mongoDbName = serviceMongo.config.db_name;
const pgUrl = servicePg.config.url;
const pgUsername = servicePg.config.username;
const pgPassword = servicePg.config.password;
const pgUrlParsed = new URL(pgUrl.replaceAll("jdbc:postgresql", "https"));
const pgHost = pgUrlParsed.hostname;
const pgDb = pgUrlParsed.pathname.replaceAll("/", "");
const client = new Client({
  node: esUri,
  auth: {
    username: esUsername,
    password: esPassword,
  },
});
const mongo = new MongoClient(mongoHost, {
  auth: {
    username: mongoUsername,
    password: mongoPassword,
  },
  authSource: mongoDbName,
  ssl: true,
  sslValidate: false,
});
const database = mongo.db(mongoDbName);
const pool = new Pool({
  user: pgUsername,
  password: pgPassword,
  host: pgHost,
  database: pgDb,
  ssl: {
    rejectUnauthorized: false,
  },
});

function getBlogsInApp() {
  const rows = database
    .collection("blogs")
    .find({})
    .map((e) => ({ _id: e._id, trashed: e.trashed }));
  return rows.toArray();
}
async function getBlogsFolderInApp() {
  const rows = database
    .collection("blogsFolders")
    .find({})
    .map((e) => ({ _id: e._id, trashed: e.trashed }));
  return await rows.toArray();
}
function getExercizerInApp() {
  return new Promise((resolve, reject) => {
    pool.query("SELECT id from exercizer.subject", (err, res) => {
      if (err) {
        console.error(err);
        return reject(err);
      }
      resolve(res.rows);
    });
  });
}
function getExercizersFolderInApp() {
  return new Promise((resolve, reject) => {
    pool.query("SELECT id from exercizer.folder", (err, res) => {
      if (err) {
        console.error(err);
        return reject(err);
      }
      resolve(res.rows);
    });
  });
}
function getFoldersInPgExplorer(app) {
  return new Promise((resolve, reject) => {
    pool.query(
      `SELECT * FROM explorer.folders WHERE application='${app}'`,
      (err, res) => {
        if (err) {
          console.error(err);
          return reject(err);
        }
        resolve(res.rows);
      }
    );
  });
}

function getResourcesInPgExplorer(app) {
  return new Promise((resolve, reject) => {
    pool.query(
      `SELECT * FROM explorer.resources WHERE application='${app}'`,
      (err, res) => {
        if (err) {
          console.error(err);
          return reject(err);
        }
        resolve(res.rows);
      }
    );
  });
}
async function getResourceES(index, body) {
  const res = await client.search({
    index,
    size: 10000,
    scroll: "1m",
    body
  });
  let all = res.body.hits.hits;
  while (true) {
    const res2 = await client.scroll({
      scroll_id: res.body["_scroll_id"],
      scroll: "1m",
    });
    if (res2.body.hits.hits.length > 0) {
      all = [...all, ...res2.body.hits.hits];
    } else {
      break;
    }
  }
  return all;
}
async function getFoldersInES(app) {
    return getResourceES("preprod-na-explorer", { query: { term: { application: app } } });
  }
async function getBlogES() {
  return getResourceES("preprod-na-blog");
}
async function getExercizerES() {
  return getResourceES("preprod-na-exercizer");
}

function countUnique(all) {
  const count = {};
  for (const a of all) {
    const id = a.id || a._id;
    count[id] = true;
  }
  const finalCount = Object.keys(count).length;
  return finalCount;
}

function compareIds(es, primary, message) {
  let failed = 0;
  const count = {};
  for (const a of es) {
    const id = a._source && a._source.assetId ? a._source.assetId : a.ent_id;
    count[id] = true;
  }
  for (const a of primary) {
    const id = a.id || a._id;
    if (!count[id]) {
      console.log(message, id, JSON.stringify(a));
      failed++;
    }
  }
  return failed;
}

function fixESFolderId(esFolder, pgExplorerFolder) {
  const pgExplorerFolderById = {};
  for (const a of pgExplorerFolder) {
    const id = a.id;
    pgExplorerFolderById[id] = a;
  }
  for (const a of esFolder) {
    const id = a.id || a._id;
    a.ent_id = pgExplorerFolderById[id] && pgExplorerFolderById[id].ent_id;
  }
}

async function countAll() {
  try {
    const blogsES = await getBlogES();
    const blogsPg = await getResourcesInPgExplorer("blog");
    const blogInApp = await getBlogsInApp();
    const exercizerES = await getExercizerES();
    const exercizerPg = await getResourcesInPgExplorer("exercizer");
    const exercizerInApp = await getExercizerInApp();
    const blogFoldrsES = await getFoldersInES("blog");
    const blogFoldrsPg = await getFoldersInPgExplorer("blog");
    const blogFolderInApp = await getBlogsFolderInApp();
    const exercizerFoldrsES = await getFoldersInES("exercizer");
    const exercizerFoldrsPg = await getFoldersInPgExplorer("exercizer");
    const exercizerFolderInApp = await getExercizersFolderInApp();
    // add ent_id to folder read from ES
    fixESFolderId(blogFoldrsES, blogFoldrsPg);
    fixESFolderId(exercizerFoldrsES, exercizerFoldrsPg);
    // compare count
    console.log(
      "Count blogs: ",
      "ES=",
      countUnique(blogsES),
      "PG=",
      countUnique(blogsPg),
      "APP=",
      countUnique(blogInApp)
    );
    console.log(
      "Count blogs folder: ",
      "ES=",
      countUnique(blogFoldrsES),
      "PG=",
      countUnique(blogFoldrsPg),
      "APP=",
      countUnique(blogFolderInApp)
    );
    console.log(
      "Count exercizer: ",
      "ES=",
      countUnique(exercizerES),
      "PG=",
      countUnique(exercizerPg),
      "APP=",
      countUnique(exercizerInApp)
    );
    console.log(
      "Count exercizer folder: ",
      "ES=",
      countUnique(exercizerFoldrsES),
      "PG=",
      countUnique(exercizerFoldrsPg),
      "APP=",
      countUnique(exercizerFolderInApp)
    );
    // check if resource missing from ES
    console.log("=====================================");
    const blogEsMissingCount = compareIds(
      blogsES,
      blogInApp,
      "Blog missing in ES"
    );
    const blogPgMissingCount = compareIds(
      blogsPg,
      blogInApp,
      "Blog missing in PG"
    );
    console.log("=====================================");
    const blogFolderEsMissingCount = compareIds(
      blogFoldrsES,
      blogFolderInApp,
      "Blog Folder missing in ES"
    );
    const blogFolderPgMissingCount = compareIds(
      blogFoldrsPg,
      blogFolderInApp,
      "Blog Folder missing in PG"
    );
    console.log("=====================================");
    const exercizerEsMissingCount = compareIds(
      exercizerES,
      exercizerInApp,
      "Exercizer missing in ES"
    );
    const exercizerPgMissingCount = compareIds(
      exercizerPg,
      exercizerInApp,
      "Exercizer missing in PG"
    );
    console.log("=====================================");
    const exercizerFolderEsMissingCount = compareIds(
      exercizerFoldrsES,
      exercizerFolderInApp,
      "Exercizer Folder missing in ES"
    );
    const exercizerFolderPgMissingCount = compareIds(
      exercizerFoldrsPg,
      exercizerFolderInApp,
      "Exercizer Folder missing in ES"
    );
    console.log("=====================================");
    console.log(
      "Blog Missing In ES count. ES=",
      blogEsMissingCount,
      "PG=",
      blogPgMissingCount
    );
    console.log(
      "Blog Folder Missing In ES count. ES=",
      blogFolderEsMissingCount,
      "PG=",
      blogFolderPgMissingCount
    );
    console.log(
      "Exercizer Missing In ES count. ES=",
      exercizerEsMissingCount,
      "PG=",
      exercizerPgMissingCount
    );
    console.log(
      "Exercizer Folder Missing In ES count. ES=",
      exercizerFolderEsMissingCount,
      "PG= ",
      exercizerFolderPgMissingCount
    );
  } catch (e) {
    console.error(e);
  }
}

countAll();
