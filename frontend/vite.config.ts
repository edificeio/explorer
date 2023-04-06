import { defineConfig, loadEnv } from "vite";
import { build, resolve } from "./config";
import react from "@vitejs/plugin-react";
import { visualizer } from "rollup-plugin-visualizer";

// https://vitejs.dev/config/
export default ({ mode }: { mode: string }) => {
  // Checking environement files
  const envFile = loadEnv(mode, process.cwd());
  const envs = { ...process.env, ...envFile };
  const hasEnvFile = Object.keys(envFile).length;

  // Proxy variables
  const headers = {
    cookie: `oneSessionId=${envs.VITE_ONE_SESSION_ID};authenticated=true; XSRF-TOKEN=${envs.VITE_XSRF_TOKEN}`,
  };
  const resHeaders = hasEnvFile
    ? {
        "set-cookie": [
          `oneSessionId=${envs.VITE_ONE_SESSION_ID}`,
          `XSRF-TOKEN=${envs.VITE_XSRF_TOKEN}`,
        ],
        "Cache-Control": "public, max-age=300",
      }
    : {};
  const proxyObj = hasEnvFile
    ? {
        target: envs.VITE_RECETTE,
        changeOrigin: true,
        headers,
      }
    : {
        target: envs.VITE_LOCALHOST || "http://localhost:8090",
        changeOrigin: false,
      };

  const proxy = {
    "/applications-list": proxyObj,
    "/conf/public": proxyObj,
    "^/(?=help-1d|help-2d)": proxyObj,
    "^/(?=assets)": proxyObj,
    "^/(?=theme|locale|i18n|skin)": proxyObj,
    // "^/(?=assets|theme|locale|i18n|skin)": proxyObj,
    "^/(?=auth|appregistry|cas|userbook|directory|communication|conversation|portal|session|timeline|workspace|infra)":
      proxyObj,
    "/blog": proxyObj,
    "/explorer": proxyObj,
  };

  return defineConfig({
    resolve,
    build,
    plugins: [react(), visualizer()],
    server: {
      proxy,
      host: "0.0.0.0",
      port: 3000,
      headers: resHeaders,
      open: true,
    },
  });
};
