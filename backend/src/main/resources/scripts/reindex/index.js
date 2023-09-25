const fs = require("fs");
const yargs = require("yargs");
const fetch = require("node-fetch").default;
const { Client } = require("@opensearch-project/opensearch");
const inquirer = require("inquirer").default;
const ALL_APPS = "all";
const FOLDER_APP = "explorer";
const ALIAS = "latest";
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
 * @param {string} url
 * @param {string} indexName
 */
async function createMapping(client, url, indexName) {
  console.log(`[createMapping] Création de l'index "${indexName}" (${url}).`);
  const resFetch = await fetch(url);
  const mapping = await resFetch.json();
  await client.indices.create({
    index: indexName,
    body: mapping,
  });
  console.log(`[createMapping] Mapping créé pour l'index "${indexName}".`);
}
/**
 *
 * @param {{indexName:string, aliasName:string, client:Client}} param0
 */
async function createAlias({ indexName, aliasName, client }) {
  console.log(`[createAlias] Création du nouvel alias ${aliasName} vers ${indexName}...`);
  await client.indices.updateAliases({
    body: {
      actions: [
        {
          add: {
            index: indexName,
            alias: aliasName,
          },
        },
      ],
    },
  });
  console.log(`[createAlias] Alias "${aliasName}" créé pour l'index "${indexName}".`);
  return undefined;
}
/**
 *
 * @param {{indexName:string, aliasName:string, client:Client}} param0
 */
async function updateAlias({ indexName, aliasName, client }) {
  try {
    const { body } = await client.indices.getAlias({ name: aliasName });
    const indices = Object.keys(body);
    if (indices.length === 0) {
      console.log(`[updateAlias] Création du nouvel alias ${aliasName} vers ${indexName}...`);
      await createAlias({ indexName, aliasName, client });
      return undefined;
    } else {
      const oldIndexName = indices[0];
      if (oldIndexName === indexName) {
        console.log(
          `[updateAlias] L'alias ${aliasName} pointe déjà sur l'index ${oldIndexName}`
        );
        return undefined;
      }
      console.log(
        `[updateAlias] Création du nouvel alias ${aliasName} vers ${indexName} (suppression de ${oldIndexName})...`
      );
      await client.indices.updateAliases({
        body: {
          actions: [
            {
              add: {
                index: indexName,
                alias: aliasName,
              },
            },
            {
              remove: {
                index: oldIndexName,
                alias: aliasName,
              },
            },
          ],
        },
      });
      console.log(`[updateAlias] Alias "${aliasName}" créé pour l'index "${indexName}".`);
      return oldIndexName;
    }
  } catch (e) {
    if (e.meta.statusCode === 404) {
      await createAlias({ aliasName, client, indexName });
      return undefined;
    }
    throw e;
  }
}
/**
 *
 * @param {{client: Client; indexName:string}} param0
 */
async function deleteIndex({ client, indexName }) {
  console.log(`[deleteIndex] Suppression de l'index ${indexName}`);
  const response = await client.indices.delete({ index: indexName });
  console.log(`[deleteIndex] Index "${indexName}" supprimé avec succès.`, response.body);
}
/**
 *
 * @param {{client:Client; aliasName: string; newIndexName:string }} param0
 */
async function reindexData({ client, aliasName, newIndexName }) {
  const { body } = await client.indices.getAlias({ name: aliasName });
  const indices = Object.keys(body);
  if (indices.length === 0) {
    console.error(`Aucun index associé à l'alias '${aliasName}'.`);
    return;
  }
  const sourceIndex = indices[0];
  console.log(`[reindexData] Reindexation en cours de ${sourceIndex} vers ${newIndexName}`);
  const res = await client.reindex({
    waitForCompletion: true,
    body: {
      source: {
        index: sourceIndex,
      },
      dest: {
        index: newIndexName,
      },
    },
  });
  console.log(
    `[reindexData] Reindexation terminé de ${sourceIndex} vers ${newIndexName} en ${res.body.took}ms (nb docs=${res.body.total})`
  );
}
/**
 *
 * @param {{listApps: string[]; gitBranch :string; confPath: string; aliasPrefix: string; deleteOld: boolean}}
 */
async function reindexAll({
  listApps,
  gitBranch,
  confPath,
  aliasPrefix,
  deleteOld,
}) {
  const conf = getConfig(confPath);
  const mappingPrefix = conf.prefix;
  const suffix = formatDateToyyyyMMddhhmmss();
  const client = new Client({
    node: conf.es.url,
    auth: {
      username: conf.es.user,
      password: conf.es.password,
    },
  });
  const safeListApps = new Set(
    listApps.includes(ALL_APPS) ? [FOLDER_APP, ...conf.apps] : listApps
  );
  for (const app of safeListApps) {
    try {
      if (app === ALL_APPS) {
        continue;
      }
      const newIndexName = `${mappingPrefix}${app}-${suffix}`;
      const aliasName = `${aliasPrefix}-${mappingPrefix}${app}`;
      if (app === FOLDER_APP) {
        const mappingUrl = `https://raw.githubusercontent.com/opendigitaleducation/explorer/${gitBranch}/backend/src/main/resources/es/mappingFolder.json`;
        await createMapping(client, mappingUrl, newIndexName);
      } else {
        const mappingUrl = `https://raw.githubusercontent.com/opendigitaleducation/explorer/${gitBranch}/backend/src/main/resources/es/mappingResource.json`;
        await createMapping(client, mappingUrl, newIndexName);
      }
      await reindexData({ aliasName, client, newIndexName });
      const oldIndexName = await updateAlias({
        aliasName,
        client,
        indexName: newIndexName,
      });
      if (oldIndexName && deleteOld) {
        await deleteIndex({ client, indexName: oldIndexName });
      }
    } catch (e) {
      console.error(
        `La réindexation a échoué pour l'application ${app}`,
        e,
        JSON.stringify(e.meta)
      );
    }
  }
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
    .option("alias-prefix", {
      alias: "ap",
      type: "string",
      default: "latest",
    })
    .option("delete-old", {
      alias: "do",
      type: "boolean",
      default: true,
    })
    .help()
    .alias("help", "h").argv;

  if (argv["list-apps"] && argv["git-branch"] && argv["conf-path"]) {
    await reindexAll({
      listApps: argv["list-apps"],
      gitBranch: argv["git-branch"],
      confPath: argv["conf-path"],
      aliasPrefix: argv["alias-prefix"],
      deleteOld: argv["delete-old"],
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
    const { gitBranch } = await inquirer.prompt({
      message: `Veuillez indiquer sur quelle branche/tags github récupérer le nouveau mapping`,
      type: "input",
      name: "gitBranch",
      default: "master",
    });
    const { prefix } = await inquirer.prompt({
      message: "Veuillez indiquer le prefix à utiliser pour l'alias",
      type: "input",
      name: "prefix",
      default: "latest",
    });
    const { deleteOld } = await inquirer.prompt([
      {
        type: "confirm",
        name: "deleteOld",
        message: `Voulez-vous supprimer l'ancien index ?`,
        default: true,
      },
    ]);
    await reindexAll({
      listApps: [listApps],
      gitBranch: gitBranch,
      confPath: pathConfig,
      aliasPrefix: prefix,
      deleteOld: deleteOld,
    });
  }
}

main();

function formatDateToyyyyMMddhhmmss() {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  const hours = String(now.getHours()).padStart(2, "0");
  const minutes = String(now.getMinutes()).padStart(2, "0");
  const seconds = String(now.getSeconds()).padStart(2, "0");

  const formattedDate = `${year}${month}${day}${hours}${minutes}${seconds}`;

  return formattedDate;
}
