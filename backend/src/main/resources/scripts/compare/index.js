const MongoClient = require("mongodb").MongoClient;
const { Client } = require("@opensearch-project/opensearch");
const yargs = require("yargs");
const inquirer = require("inquirer").default;
const fs = require("fs");
const FOLDER_APP = "explorer";
const DB_NAME = "ent";
const OS_OUTPUT = "opensearch-ids.txt";
const MONGO_OUTPUT = "mongo-ids.txt";
const DIFF_OUTPUT = "diff-ids.txt";
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
      apps: [FOLDER_APP, ...explorerConfig.applications],
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
/**
 *
 * @param {string} confPath
 * @param {string} dbName
 * @param {string} collectionName
 */
function getMongoIds(confPath, dbName, collectionName) {
  return new Promise((resolve, reject) => {
    // get mongo config
    const config = getMongoSearchConfig(confPath);
    const url = `mongodb://${config.username}:${config.password}@${config.host}:${config.port}?authSource=${config.db_auth}&ssl=true`;
    // init mongo client
    const mongoClient = new MongoClient(url, {
      checkServerIdentity: false,
      sslValidate: false,
      connectTimeoutMS: 10000,
    });
    console.log("Starting mongo extract for collection: ", url);
    // reset output file
    fs.writeFileSync(MONGO_OUTPUT, "");
    // connect to mongo
    mongoClient.connect((err) => {
      if (err) {
        console.error("Fail to connect to mongo", err);
        reject(err);
        return;
      }
      console.log("Mongo collection established: ", dbName, collectionName);
      // find using stream
      const db = mongoClient.db(dbName);
      const collection = db.collection(collectionName);
      const cursor = collection.find();
      const mongoStream = cursor.stream();
      // on data append to file
      mongoStream.on("data", (item) => {
        fs.appendFileSync(MONGO_OUTPUT, item._id.toString() + "\n");
      });
      // on error log
      mongoStream.on("error", (e) => {
        console.log("MongoDB error occured", e);
        reject(e);
      });
      // on finish close connection
      mongoStream.on("end", () => {
        console.log("MongoDB data has been written to mongo-ids.txt");
        mongoClient.close();
        resolve();
      });
    });
  });
}
/**
 *
 * @param {string} confPath
 * @param {string} app
 * @param {number} from
 */
async function getOpenSearchIds(confPath, app) {
  // get opensearch config
  const conf = getOpenSearchConfig(confPath);
  const mappingPrefix = conf.prefix;
  const aliasName = `${mappingPrefix}${app}`;
  console.log("Starting openseach extract for index:", aliasName);
  //init opensearch client
  const esClient = new Client({
    node: conf.es.url,
    auth: {
      username: conf.es.user,
      password: conf.es.password,
    },
    log: "error",
  });
  // reset output file
  fs.writeFileSync(OS_OUTPUT, "");
  // make first search and init scroll
  let response = await esClient.search({
    index: aliasName,
    scroll: "300s",
    body: {
      query: {
        match_all: {},
      },
      _source: ["_id", "assetId"],
      size: 1000,
    },
  });
  console.log(
    "Fetched",
    response.body.hits.hits.length,
    "Total",
    response.body.hits.total.value
  );
  if (response.body.hits.hits.length > 0) {
    // append ids to file
    let esIds = response.body.hits.hits.map((hit) => hit._source.assetId);
    fs.appendFileSync(OS_OUTPUT, esIds.join("\n") + "\n");
    // while scroll return response continue
    while (response.body.hits.total.value > esIds.length) {
      // fetch next scroll
      response = await esClient.scroll({
        scrollId: response.body._scroll_id,
        scroll: "10m",
      });
      console.log(
        "Fetched",
        response.body.hits.hits.length,
        "Total",
        response.body.hits.total.value
      );
      if (response.body.hits.hits.length > 0) {
        // if any response append to file
        esIds = response.body.hits.hits.map((hit) => hit._source.assetId);
        fs.appendFileSync(OS_OUTPUT, esIds.join("\n") + "\n");
      } else {
        // if no response sopt
        break;
      }
    }
    // stopped because scroll has finished
    console.log("Elasticsearch data has been written to opensearch-ids.txt");
  } else {
    // stopped because index is empty
    console.log(
      "Elasticsearch data has NOT been written to opensearch-ids.txt (empty)"
    );
  }
}

/**
 *
 * @param {string} file1
 * @param {string} file2
 * @param {string} outputFile
 */
function compareFiles(file1, file2, outputFile) {
  console.log("Comparing files:", file1, file2)
  // read data
  const data1 = fs.readFileSync(file1, "utf-8");
  const data2 = fs.readFileSync(file2, "utf-8");
  // parse lines
  const array1 = data1.split("\n");
  const array2 = data2.split("\n");
  // optimize using set
  const set2 = new Set(array2);
  // compare
  const diff = array1.filter((item) => !set2.has(item));
  // write to file
  console.log("Writting to output:", outputFile)
  fs.writeFileSync(outputFile, diff.join("\n"));
}

/**
 *
 * @param {string} confPath
 * @param {string} app
 * @param {string} dbName
 * @param {string} collectionName
 */
async function compare(confPath, app, dbName, collectionName) {
  try {
    await getOpenSearchIds(confPath, app, 0);
    await getMongoIds(confPath, dbName, collectionName);
    compareFiles(MONGO_OUTPUT, OS_OUTPUT, DIFF_OUTPUT);
  } catch (e) {
    console.error("Failed:", e);
  }
}

async function main() {
  const DEFAULT_PATH = "/srv/vertx/entcore/conf/entcore.conf";
  const argv = yargs
    .option("application", {
      alias: "la",
      choices: ["blog", "exercizer", FOLDER_APP].sort(),
      array: true,
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
    .help()
    .alias("help", "h").argv;

  if (
    argv["application"] &&
    argv["conf-path"] &&
    argv["db-name"] &&
    argv["collection-name"]
  ) {
    await compare(
      argv["conf-path"],
      argv["application"],
      argv["db-name"],
      argv["collection-name"]
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
    await compare(pathConfig, application, dbName, colName);
  }
}

main();
