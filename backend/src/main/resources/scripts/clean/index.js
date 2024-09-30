const fs = require("fs");
const yargs = require("yargs");
const { Client: OSClient } = require("@opensearch-project/opensearch");
const { MongoClient } = require("mongodb");
const inquirer = require("inquirer").default;
const { Client: PGClient } = require("pg");
/**
 *
 * @param {string} path
 * @returns {{apps:string[],host:string, prefix:string, es:{url:string, user:string, password:string}}}
 */
function getOpenSearchConfig(path) {
  const configBuffer = fs.readFileSync(path);
  const config = JSON.parse(configBuffer.toString());
  const found = config.services.filter((service) =>
    (service.name || "").startsWith("com.opendigitaleducation~explorer")
  );
  if (found.length) {
    const explorerConfig = found[0].config;
    return {
      host: explorerConfig.host,
      apps: [...explorerConfig.applications],
      prefix: explorerConfig["index-prefix"],
      es: {
        url: explorerConfig.elasticsearchConfig.uris[0],
        user: explorerConfig.elasticsearchConfig.user,
        password: explorerConfig.elasticsearchConfig.password,
      },
    };
  } else {
    console.error("Le fichier de configuration est introuvable: ", path);
    process.exit(-1);
  }
}
/**
 *
 * @param {string} path
 * @returns {{host:string, port:number, username:string, password:string, db_auth:string}}
 */
function getMongoSearchConfig(path) {
  const configBuffer = fs.readFileSync(path);
  const config = JSON.parse(configBuffer.toString());
  const found = config.services.filter((service) =>
    (service.name || "").startsWith("io.vertx~mod-mongo-persistor")
  );
  if (found.length) {
    const mongoConf = found[0].config;
    const seed = found[0].config.seeds[0];
    return {
      host: seed.host,
      port: seed.port,
      username: mongoConf.username,
      password: mongoConf.password,
      db_auth: mongoConf.db_auth,
    };
  } else {
    console.error("Le fichier de configuration est introuvable: ", path);
    process.exit(-1);
  }
}

function getPgConfig(path) {
  const configBuffer = fs.readFileSync(path);
  const config = JSON.parse(configBuffer.toString());
  const found = config.services.filter((service) =>
    (service.name || "").startsWith("org.entcore~infra")
  );
  if (found.length) {
    const postgresConfig = found[0].config.postgresConfig;
    return {
      host: postgresConfig.host,
      database: postgresConfig.database,
      sslMode: postgresConfig["ssl-mode"],
      port: postgresConfig.port,
      user: postgresConfig.user,
      password: postgresConfig.password,
      poolSize: postgresConfig["pool-size"],
    };
  } else {
    console.error("Le fichier de configuration est introuvable: ", path);
    process.exit(-1);
  }
}

/**
 *
 * @param {string} confPath
 * @param {string} app
 * @param {string} mongoDbName
 * @param {string} mongoCollectionName
 * @param {boolean} execDelete
 */
async function clean(
  confPath,
  app,
  mongoDbName,
  mongoCollectionName,
  execDelete
) {
  // prepare mongoclient
  const mongoConfig = getMongoSearchConfig(confPath);
  const mongoUrl = `mongodb://${mongoConfig.username}:${mongoConfig.password}@${mongoConfig.host}:${mongoConfig.port}?authSource=${mongoConfig.db_auth}&ssl=true`;
  const mongoClient = new MongoClient(mongoUrl, {
    checkServerIdentity: false,
    sslValidate: false,
    connectTimeoutMS: 10000,
  });
  await new Promise((resolve, reject) => {
    mongoClient.connect((err) => {
      if (err) {
        reject(err);
      } else {
        resolve();
      }
    });
  });
  const mongoDb = mongoClient.db(mongoDbName);
  const mongoCollection = mongoDb.collection(mongoCollectionName);
  //prepare opensearchclient
  const osConfig = getOpenSearchConfig(confPath);
  const osClient = new OSClient({
    node: osConfig.es.url,
    auth: {
      username: osConfig.es.user,
      password: osConfig.es.password,
    },
    log: "error",
  });
  // prepare pg client
  const pgConf = getPgConfig(confPath);
  const pgClient = new PGClient({
    host: pgConf.host,
    password: pgConf.password,
    port: pgConf.port,
    database: pgConf.database,
    ssl: {
      rejectUnauthorized: false,
    },
    user: pgConf.user,
  });
  await pgClient.connect();
  // prepare index
  const mappingPrefix = osConfig.prefix;
  const osIndex = `${mappingPrefix}${app}`;
  // delete opensearch
  const onOpensearchDelete = async (id) => {
    const res = await osClient.delete({
      routing: app,
      index: osIndex,
      id: id,
    });
    console.log(`Deleted from OpenSearch:  _id=${id},res=${res} `);
  };
  // delete pg
  const onPgDelete = async (id) => {
    const res = await pgClient.query(
      "DELETE FROM explorer.resources WHERE id = $1",
      [id]
    );
    console.log(`Deleted from PostgreSQL: rows=${res.rowCount}, _id=${id} `);
  };
  // process documents
  var count = 0;
  const onDocumentHits = async (hits) => {
    for (const { _id, _source } of hits) {
      const { assetId } = _source;
      const mongoDoc = await mongoCollection.findOne({ _id: assetId });
      if (!mongoDoc) {
        try {
          console.log(
            `Mongo document is missing: collection=${mongoCollectionName}, _id=${assetId}`
          );
          if(execDelete){
            await onPgDelete(_id);
            await onOpensearchDelete(_id);
          }
        } catch (e) {
          console.error(`Could not delete doc: _id=${assetId}`, e);
        }
      }
    }
    count += hits.length;
    console.log("Number of documents iterated: ", count);
  };
  // iterate over documents
  const pageSize = 1000;
  let {
    body: { _scroll_id, hits },
  } = await osClient.search({
    index: osIndex,
    body: {
      size: pageSize,
      query: {
        match_all: {},
      },
    },
    scroll: "5m",
  });
  await onDocumentHits(hits.hits);
  // iterate using scroll
  let scrollId = _scroll_id;
  while (hits.hits.length > 0) {
    const { body } = await osClient.scroll({
      scrollId: scrollId,
      scroll: "5m",
    });
    hits = body.hits;
    await onDocumentHits(hits.hits);
    _scroll_id = body._scroll_id;
    //keep scrollid
    scrollId = _scroll_id;
  }
  console.log("Finished");
  await osClient.close();
}

async function main() {
  const DEFAULT_PATH = "/srv/vertx/entcore/conf/entcore.conf";
  const DB_NAME = "ent";
  const argv = yargs
    .option("app", {
      alias: "app",
      choices: ["blog", "exercizer", "mindmap"].sort(),
      array: false,
    })
    .option("conf-path", {
      alias: "cp",
      type: "string",
      default: DEFAULT_PATH,
    })
    .option("db-name", {
      alias: "db",
      type: "string",
      default: DB_NAME,
    })
    .option("collection-name", {
      alias: "cn",
      type: "string",
    })
    .option("delete", {
      alias: "del",
      type: "boolean",
      default: false,
    })
    .help()
    .alias("help", "h").argv;

  if (
    argv["conf-path"] &&
    argv["app"] &&
    argv["db-name"] &&
    argv["collection-name"] &&
    argv["delete"]
  ) {
    await clean(
      argv["conf-path"],
      argv["app"],
      argv["db-name"],
      argv["collection-name"],
      argv["delete"]
    );
  } else {
    const { pathConfig } = await inquirer.prompt({
      message: "Veuillez indiquer le chemin vers le fichier de configuration",
      type: "input",
      name: "pathConfig",
      default: DEFAULT_PATH,
    });
    const config = getOpenSearchConfig(pathConfig);
    const { application } = await inquirer.prompt({
      message: `Veuillez sélectionner l'application à migrer`,
      type: "list",
      name: "application",
      choices: config.apps.sort(),
    });
    const { dbName } = await inquirer.prompt({
      message: "Veuillez indiquer le nom de la base de données",
      type: "input",
      name: "dbName",
      default: DB_NAME,
    });
    const { colName } = await inquirer.prompt({
      message: "Veuillez indiquer le nom de la collection ou table",
      type: "input",
      name: "colName",
      default: application,
    });
    const { del } = await inquirer.prompt({
      message: `Voulez vous supprimer les données?`,
      type: "confirm",
      name: "del",
    });
    await clean(pathConfig, application, dbName, colName, del);
  }
}
main();
