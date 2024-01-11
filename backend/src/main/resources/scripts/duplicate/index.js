const fs = require("fs");
const yargs = require("yargs");
const { Client: ESClient } = require("@opensearch-project/opensearch");
const inquirer = require("inquirer").default;
const { Client: PGClient } = require("pg");
const ALL_APPS = "all";
const FOLDER_APP = "explorer";
/**
 *
 * @param {string} path
 * @returns {{apps:string[],host:string, prefix:string, es:{url:string, user:string, password:string}}}
 */
function getConfig(path) {
  const configBuffer = fs.readFileSync(path);
  const config = JSON.parse(configBuffer.toString());
  const found = config.services.filter((service) =>
    (service.name || "").startsWith("com.opendigitaleducation~explorer")
  );
  if (found.length) {
    const explorerConfig = found[0].config;
    return {
      host: explorerConfig.host,
      apps: [...explorerConfig.applications, ALL_APPS],
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
async function getOpenSearchData({ confPath, app }) {
  const data = {};
  const conf = getConfig(confPath);
  const mappingPrefix = conf.prefix;
  const esIndex = `${mappingPrefix}${app}`;
  const esClient = new ESClient({
    node: conf.es.url,
    auth: {
      username: conf.es.user,
      password: conf.es.password,
    },
  });
  // add function
  const add = (hits) => {
    for (const { _id, _source } of hits) {
      const { assetId, application } = _source;
      const entId = app === FOLDER_APP ? _id : assetId;
      const key = application + "_" + entId;
      if (!data[key]) {
        data[key] = [];
      }
      // search if not exists
      const find = data[key].find((d) => d.id === _id);
      if (!find) {
        data[key].push({
          id: _id,
          entId,
          app: application,
        });
      }
    }
  };
  // create scroll
  const pageSize = 100;
  let {
    body: { _scroll_id, hits },
  } = await esClient.search({
    index: esIndex,
    body: {
      size: pageSize,
      query: {
        match_all: {},
      },
    },
    scroll: "5m",
  });
  // add hits
  add(hits.hits);
  // iterate scroll
  let scrollId = _scroll_id;
  while (hits.hits.length > 0) {
    const { body } = await esClient.scroll({
      scrollId: scrollId,
      scroll: "5m",
    });
    hits = body.hits;
    _scroll_id = body._scroll_id;
    // add hits
    add(hits.hits);
    //keep scrollid
    scrollId = _scroll_id;
  }
  await esClient.close();
  return data;
}

async function deleteOpensearch({ app, confPath, ids, del }) {
  const conf = getConfig(confPath);
  const mappingPrefix = conf.prefix;
  const esIndex = `${mappingPrefix}${app}`;
  const esClient = new ESClient({
    node: conf.es.url,
    auth: {
      username: conf.es.user,
      password: conf.es.password,
    },
  });
  for (const id of ids) {
    console.log(`deleting;${esIndex};${id};${del}`);
    if (del) {
      await esClient.delete({
        routing: app,
        index: esIndex,
        id: id,
      });
    }
  }
  await esClient.close();
}

async function getPgIds({ confPath, entIds }) {
  const conf = getPgConfig(confPath);
  const pgClient = new PGClient({
    host: conf.host,
    password: conf.password,
    port: conf.port,
    database: conf.database,
    ssl: {
      rejectUnauthorized: false,
    },
    user: conf.user,
  });
  const data = {};
  if(entIds.length===0){
    return data
  }
  await pgClient.connect();
  // start cursor
  const criteria = entIds.map((id) => `'${id}'`).join(",");
  const cursor = await pgClient.query(
    `SELECT id, ent_id, application FROM explorer.resources WHERE ent_id IN (${criteria})`
  );
  for (const row of cursor.rows) {
    const key = row.application + "_" + row.ent_id;
    data[key] = { id: row.id, entId: row.ent_id };
  }
  await pgClient.end();
  return data;
}
async function getDuplicate({ confPath, listApps, del }) {
  const conf = getConfig(confPath);
  const safeListApps = listApps.includes(ALL_APPS) ? [...conf.apps] : listApps;
  for (const app of safeListApps) {
    if (app === ALL_APPS || app === FOLDER_APP) {
      continue;
    }
    const openData = await getOpenSearchData({ confPath, app });
    const duplicates = [];
    // get duplicated
    for (const key in openData) {
      const values = openData[key];
      if (values.length === 0) {
        continue;
      }
      const ids = values.map((v) => v.id);
      const app = values[0].app;
      const entId = values[0].entId;
      if (values.length > 1) {
        console.log(`duplicate;${key};${entId};${app};${JSON.stringify(ids)}`);
        duplicates.push({ entId, ids });
      } else {
        console.log(`single;${key};${entId};${app};${JSON.stringify(ids)}`);
      }
    }
    // search existing in pg
    const pgIds = await getPgIds({
      confPath,
      entIds: duplicates.map((d) => d.entId),
    });
    const toDelete = [];
    for (const key in pgIds) {
      const value = pgIds[key];
      const values = openData[key];
      const duplicateIds = values.map((v) => v.id);
      const pgId = value.id;
      for (const duplicateId of duplicateIds) {
        if (duplicateId != pgId) {
          console.log(
            `delete;${key};${duplicateId};${JSON.stringify(
              duplicateIds
            )};${pgId}`
          );
          toDelete.push(duplicateId);
        }
      }
    }
    // delete
    await deleteOpensearch({ app, confPath, ids: toDelete, del });
  }
}

async function main() {
  const DEFAULT_PATH = "/srv/vertx/entcore/conf/entcore.conf";
  const argv = yargs
    .option("list-apps", {
      alias: "la",
      choices: ["blog", "exercizer", "mindmap", ALL_APPS].sort(),
      array: true,
    })
    .option("conf-path", {
      alias: "cp",
      type: "string",
      default: DEFAULT_PATH,
    })
    .option("delete", {
      alias: "del",
      type: "boolean",
      default: false,
    })
    .help()
    .alias("help", "h").argv;

  if (argv["list-apps"] && argv["conf-path"]) {
    await getDuplicate({
      listApps: argv["list-apps"],
      confPath: argv["conf-path"],
      del: argv["delete"],
    });
  } else {
    const { pathConfig } = await inquirer.prompt({
      message: "Veuillez indiquer le chemin vers le fichier de configuration",
      type: "input",
      name: "pathConfig",
      default: DEFAULT_PATH,
    });
    const config = getConfig(pathConfig);
    const { listApps } = await inquirer.prompt({
      message: `Veuillez sélectionner la liste des applications à migrer`,
      type: "list",
      name: "listApps",
      choices: config.apps.sort(),
    });
    const { del } = await inquirer.prompt({
      message: `Voulez vous supprimer les données?`,
      type: "confirm",
      name: "del",
    });
    await getDuplicate({
      listApps: [listApps],
      confPath: pathConfig,
      del,
    });
  }
}
main();
