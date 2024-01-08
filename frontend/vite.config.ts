import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";

// https://vitejs.dev/config/
export default ({ mode }: { mode: string }) => {
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

  const build = {
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

  const plugins = [react(), tsconfigPaths()];

  const server = {
    proxy,
    host: "0.0.0.0",
    port: 3000,
    headers,
    open: true,
  };

  return defineConfig({
    build,
    plugins,
    server,
  });
};
