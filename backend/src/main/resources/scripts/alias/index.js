const fs = require("fs");
const yargs = require("yargs");
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
 * @param {{indexName:string, aliasName:string, client:Client}} param0
 */
async function createAlias({ indexName, aliasName, client }) {
  console.log(`Création du nouvel alias ${aliasName} vers ${indexName}...`);
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
  console.log(`Alias "${aliasName}" créé pour l'index "${indexName}".`);
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
      console.log(`Création du nouvel alias ${aliasName} vers ${indexName}...`);
      await createAlias({ indexName, aliasName, client });
      return undefined;
    } else {
      const oldIndexName = indices[0];
      if (oldIndexName === indexName) {
        console.log(
          `L'alias ${aliasName} pointe déjà sur l'index ${oldIndexName}`
        );
        return undefined;
      }
      console.log(
        `Création du nouvel alias ${aliasName} vers ${indexName} (suppression de ${oldIndexName})...`
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
              }
            },
          ],
        },
      });
      console.log(`Alias "${aliasName}" créé pour l'index "${indexName}".`);
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
 * @param {string} aliasPrefix
 * @param {string} confPath
 * @param {Array<string>} listApps
 */
async function createAliases(aliasPrefix, confPath, listApps) {
  const conf = getConfig(confPath);
  const client = new Client({
    node: conf.es.url,
    auth: {
      username: conf.es.user,
      password: conf.es.password,
    },
  });
  try {
    const mappingPrefix = conf.prefix;
    const safeListApps = listApps.includes(ALL_APPS)
      ? [FOLDER_APP, ...conf.apps]
      : listApps;
    for (const app of safeListApps) {
      try {
        if (app === ALL_APPS) {
          continue;
        }
        const indexName = `${mappingPrefix}${app}`;
        const aliasName = `${aliasPrefix}-${mappingPrefix}${app}`;
        await updateAlias({ aliasName, client, indexName });
      } catch (e) {
        console.error(`La création de l'alias a échoué pour l'app: ${app}`, e);
      }
    }
  } finally {
    client.close();
  }
}

async function main() {
  const DEFAULT_PATH = "/srv/vertx/entcore/conf/entcore.conf";
  const argv = yargs
    .option("alias-prefix", {
      alias: "pe",
      type: "string",
      default: ALIAS,
    })
    .option("list-apps", {
      alias: "la",
      choices: ["blog", "exercizer", FOLDER_APP, ALL_APPS].sort(),
      array: true,
    })
    .option("conf-path", {
      alias: "cp",
      type: "string",
      default: DEFAULT_PATH,
    })
    .help()
    .alias("help", "h").argv;

  if (argv["alias-prefix"] && argv["conf-path"] && argv["list-apps"]) {
    await createAliases(
      argv["alias-prefix"],
      argv["conf-path"],
      argv["list-apps"]
    );
  } else {
    const { prefix } = await inquirer.prompt({
      message: "Veuillez indiquer le prefix à utiliser pour l'alias",
      type: "input",
      name: "prefix",
      default: ALIAS,
    });
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
    await createAliases(prefix, pathConfig, [listApps]);
  }
}

main();
