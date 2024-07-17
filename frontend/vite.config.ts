import react from "@vitejs/plugin-react";
import { resolve } from "node:path";
import { BuildOptions, defineConfig, loadEnv, Plugin } from "vite";
import dts from "vite-plugin-dts";
import tsconfigPaths from "vite-tsconfig-paths";

import { createHash } from "node:crypto";
import { dependencies } from "./package.json";

const hash = createHash("md5")
  .update(Date.now().toString())
  .digest("hex")
  .substring(0, 8);

const queryHashVersion = `v=${hash}`;

function hashEdificeBootstrap(): Plugin {
  return {
    name: "vite-plugin-edifice",
    apply: "build",
    transformIndexHtml(html) {
      return html.replace(
        "/assets/themes/edifice-bootstrap/index.css",
        `/assets/themes/edifice-bootstrap/index.css?${queryHashVersion}`,
      );
    },
  };
}

// https://vitejs.dev/config/
export default ({ mode }: { mode: string }) => {
  // Checking environment files
  const envFile = loadEnv(mode, process.cwd());
  const envs = { ...process.env, ...envFile };
  const hasEnvFile = Object.keys(envFile).length;

  const isProduction = mode === "production";
  const isLibMode = mode === "lib";

  // Proxy variables
  const headers = hasEnvFile
    ? {
        "set-cookie": [
          `oneSessionId=${envs.VITE_ONE_SESSION_ID}`,
          `XSRF-TOKEN=${envs.VITE_XSRF_TOKEN}`,
        ],
        "Cache-Control": "public, max-age=300",
      }
    : {};

  const proxyObj = envs.VITE_RECETTE
    ? {
        target: envs.VITE_RECETTE,
        changeOrigin: true,
        headers: {
          cookie: `oneSessionId=${envs.VITE_ONE_SESSION_ID};authenticated=true; XSRF-TOKEN=${envs.VITE_XSRF_TOKEN}`,
        },
      }
    : {
        target: envs.VITE_LOCALHOST || "http://localhost:8090",
        changeOrigin: false,
      };

  const proxy = {
    "/applications-list": proxyObj,
    "/conf/public": proxyObj,
    "^/(?=help-1d|help-2d)": proxyObj,
    "^/(?=assets|theme|locale|i18n|skin)": proxyObj,
    "^/(?=archive|auth|appregistry|cas|userbook|directory|communication|conversation|portal|session|timeline|workspace|infra)":
      proxyObj,
    "^/(?=blog|mindmap|scrapbook|collaborativewall)": proxyObj,
    "/xiti": proxyObj,
    "/analyticsConf": proxyObj,
    "/explorer": proxyObj,
  };

  const build: BuildOptions = {
    assetsDir: "assets/js/ode-explorer/",
    cssCodeSplit: false,
    rollupOptions: {
      external: ["edifice-ts-client"],
      output: {
        inlineDynamicImports: true,
        paths: {
          "edifice-ts-client": `/assets/js/edifice-ts-client/index.js?${queryHashVersion}`,
        },
        entryFileNames: `[name].js`,
        chunkFileNames: `[name].js`,
        assetFileNames: `[name].[ext]`,
      },
    },
  };

  const buildLib: BuildOptions = {
    outDir: "lib",
    lib: {
      entry: {
        index: resolve(__dirname, "src/index.ts"),
      },
      formats: ["es"],
    },
    rollupOptions: {
      treeshake: true,
      external: [
        ...Object.keys(dependencies || {}),
        "react/jsx-runtime",
        "edifice-ts-client",
        "@edifice-ui/icons/nav",
      ],
      output: {
        entryFileNames: `[name].js`,
        chunkFileNames: `[name].js`,
        assetFileNames: `[name].[ext]`,
      },
    },
  };

  const reactPlugin = react(
    isLibMode
      ? {
          babel: {
            plugins: ["@babel/plugin-transform-react-pure-annotations"],
          },
        }
      : {},
  );

  const dtsPlugin = isLibMode && dts();

  const plugins = [
    reactPlugin,
    dtsPlugin,
    tsconfigPaths(),
    hashEdificeBootstrap(),
  ];

  const server = {
    proxy,
    host: "0.0.0.0",
    port: 3000,
    headers,
  };

  return defineConfig({
    build: isProduction ? build : buildLib,
    plugins,
    server,
  });
};
