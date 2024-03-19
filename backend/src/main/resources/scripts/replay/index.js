const inquirer = require("inquirer").default;
const fetch = require("node-fetch").default;
const yargs = require("yargs");
const fs = require("fs");
/**
 *
 * @param {string} base
 * @param {string} application
 * @param {string} file
 */
async function replay(base, application, file, cookie) {
  const data = fs.readFileSync(file, "utf-8");
  const array = data.split("\n");
  for (const id of array) {
    if (id) {
      const url = `${base}${
        base.endsWith("/") ? "" : "/"
      }explorer/reindex/${application}/${application}?ids=${id}`;
      const res = await fetch(url, {
        headers: { Cookie: `oneSessionId=${cookie}` },
      });
      if (!res.ok) {
        console.error("Failed:", url, res.status);
      }
    }
  }
}

async function main() {
  const argv = yargs
    .option("url", {
      alias: "u",
    })
    .option("application", {
      alias: "a",
      type: "string",
    })
    .option("input", {
      alias: "i",
      type: "string",
    })
    .option("cookie", {
      alias: "c",
      type: "string",
    })
    .help()
    .alias("help", "h").argv;

  if (argv["url"] && argv["application"] && argv["input"] && argv["cookie"]) {
    await replay(
      argv["url"],
      argv["application"],
      argv["input"],
      argv["cookie"]
    );
  } else {
    const { baseUrl } = await inquirer.prompt({
      message: "Veuillez l'url de base",
      type: "input",
      name: "baseUrl",
      default: "https://oneconnect.opendigitaleducation.com",
    });
    const { application } = await inquirer.prompt({
      message: `Veuillez indiquer l'application concerné`,
      type: "list",
      name: "application",
    });
    const { inputFile } = await inquirer.prompt({
      message: "Veuillez indiquer le nom du fichier en entrée",
      type: "input",
      name: "inputFile",
    });
    const { cookie } = await inquirer.prompt({
      message: "Veuillez indiquer la valeur du cookie de session",
      type: "input",
      name: "cookie",
    });
    await replay(baseUrl, application, inputFile, cookie);
  }
}

main();
