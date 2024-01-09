import { BuildOptions, defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";
import { resolve } from "path";

import { dependencies } from "./package.json";

// https://vitejs.dev/config/
export default ({ mode }: { mode: string }) => {
  console.log({ mode });
  // Checking environment files
  const envFile = loadEnv(mode, process.cwd());
  const envs = { ...process.env, ...envFile };
  const hasEnvFile = Object.keys(envFile).length;

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
    "^/(?=auth|appregistry|cas|userbook|directory|communication|conversation|portal|session|timeline|workspace|infra)":
      proxyObj,
    "^/(?=blog|mindmap)": proxyObj,
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
        paths: {
          "edifice-ts-client": "/assets/js/edifice-ts-client/index.js",
        },
        entryFileNames: `[name].js`,
        chunkFileNames: `[name].js`,
        assetFileNames: `[name].[ext]`,
      },
    },
  };

  const buildLib: BuildOptions = {
    outDir: "dist",
    lib: {
      // Could also be a dictionary or array of multiple entry points
      entry: resolve(__dirname, "src/index.ts"),
      formats: ["es"],
    },
    rollupOptions: {
      // make sure to externalize deps that shouldn't be bundled
      // into your library
      external: [
        ...Object.keys(dependencies),
        "swiper/react",
        "swiper/modules",
        "react/jsx-runtime",
        "@edifice-ui/icons/nav",
      ],
      /* output: {
        entryFileNames: `[name].js`,
        chunkFileNames: `[name].js`,
        assetFileNames: `[name].[ext]`,
      }, */
    },
  };

  const plugins = [
    mode === "production"
      ? react()
      : react({
          babel: {
            plugins: ["@babel/plugin-transform-react-pure-annotations"],
          },
        }),
    tsconfigPaths(),
  ];

  const server = {
    proxy,
    host: "0.0.0.0",
    port: 3000,
    headers,
    open: true,
  };

  return defineConfig({
    build: mode === "production" ? build : buildLib,
    plugins,
    server,
  });
};
