const fs = require("fs");
const yargs = require("yargs");
const fetch = require("node-fetch").default;
const { Client } = require("@opensearch-project/opensearch");
const inquirer = require("inquirer").default;
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
      apps: [FOLDER_APP, ...explorerConfig.applications, ALL_APPS],
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
 * @param {Client} client
 * @param {string} indexName
 */
async function deleteMapping(client, indexName) {
  try {
    console.log(`Suppression du schéma "${indexName}".`);
    await client.indices.delete({ index: indexName });
    console.log(`Mapping supprimé pour l'index "${indexName}".`);
  } catch (error) {
    console.error(
      `Une erreur s'est produite lors de la suppression du mapping : ${error.message}`
    );
  }
}
/**
 *
 * @param {Client} client
 * @param {string} url
 * @param {string} indexName
 */
async function createMapping(client, url, indexName) {
  try {
    console.log(`Création du schéma "${indexName}" (${url}).`);
    const resFetch = await fetch(url);
    const mapping = await resFetch.json();
    await client.indices.create({
      index: indexName,
      body: mapping,
    });
    console.log(`Mapping créé pour l'index "${indexName}".`);
  } catch (error) {
    console.error(
      `Une erreur s'est produite lors de la création du mapping : ${error.message}`
    );
  }
}
/**
 *
 * @param {string[]} listApps
 * @param {string} gitBranch
 * @param {string} confPath
 * @param {boolean} skipCheck
 */
async function migrate(listApps, gitBranch, confPath, skipCheck) {
  if (!skipCheck) {
    let doCheck = false;
    do {
      const { check } = await inquirer.prompt({
        message: `Avez vous désactiver le job de migration?`,
        type: "confirm",
        name: "check",
      });
      doCheck = check;
    } while (!doCheck);
  }
  const conf = getConfig(confPath);
  const mappingPrefix = conf.prefix;
  const client = new Client({
    node: conf.es.url,
    auth: {
      username: conf.es.user,
      password: conf.es.password,
    },
  });
  const safeListApps = listApps.includes(ALL_APPS)
    ? [FOLDER_APP, ...conf.apps]
    : listApps;
  for (const app of safeListApps) {
    if (app === ALL_APPS) {
      continue;
    }
    const indexName = `${mappingPrefix}${app}`;
    if (app === FOLDER_APP) {
      const mappingUrl = `https://raw.githubusercontent.com/opendigitaleducation/explorer/${gitBranch}/backend/src/main/resources/es/mappingFolder.json`;
      await deleteMapping(client, indexName);
      await createMapping(client, mappingUrl, indexName);
    } else {
      const mappingUrl = `https://raw.githubusercontent.com/opendigitaleducation/explorer/${gitBranch}/backend/src/main/resources/es/mappingResource.json`;
      await deleteMapping(client, indexName);
      await createMapping(client, mappingUrl, indexName);
    }
  }
  console.log(
    `Veuillez réactiver le job d'indexation et lancer la réindexation via l'URL: ${conf.host}/explorer/reindex/all/all?include_old_folders=true&include_new_folders=true`
  );
}

async function main() {
  const DEFAULT_PATH = "/srv/vertx/entcore/conf/entcore.conf";
  const argv = yargs
    .option("list-apps", {
      alias: "la",
      choices: ["blog", "exercizer", FOLDER_APP, ALL_APPS].sort(),
      array: true,
    })
    .option("git-branch", {
      alias: "gb",
      type: "string",
      default: "master",
    })
    .option("conf-path", {
      alias: "cp",
      type: "string",
      default: DEFAULT_PATH,
    })
    .option("skip-check", {
      alias: "sc",
      type: "boolean",
      default: false,
    })
    .help()
    .alias("help", "h").argv;

  if (argv["list-apps"] && argv["git-branch"] && argv["conf-path"]) {
    await migrate(
      argv["list-apps"],
      argv["git-branch"],
      argv["conf-path"],
      argv["skip-check"]
    );
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
    const { gitBranch } = await inquirer.prompt({
      message: `Veuillez indiquer sur quelle branche/tags github récupérer le nouveau mapping`,
      type: "input",
      name: "gitBranch",
      default: "master",
    });
    await migrate([listApps], gitBranch, pathConfig, false);
  }
}

main();
