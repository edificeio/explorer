import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tsconfigPaths from "vite-tsconfig-paths";

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
    "^/(?=assets|theme|locale|i18n|skin)": proxyObj,
    "^/(?=auth|appregistry|cas|userbook|directory|communication|conversation|portal|session|timeline|workspace|infra)":
      proxyObj,
    "^/(?=blog|mindmap)": proxyObj,
    "/xiti": proxyObj,
    "/analyticsConf": proxyObj,
    "/explorer": proxyObj,
  };

  return defineConfig({
    build: {
      assetsDir: "assets/js/ode-explorer/",
      cssCodeSplit: false,
      rollupOptions: {
        external: [
          "ode-ts-client" /* "@edifice-ui/react", "@edifice-ui/icons" */,
        ],
        output: {
          // inlineDynamicImports: true,
          paths: {
            "ode-ts-client": "/assets/js/ode-ts-client/ode-ts-client.esm.js",
            // "@edifice-ui/react": "/assets/js/@edifice-ui/react/index.js",
            // "@edifice-ui/icons": "/assets/js/@edifice-ui/icons/index.js",
          },
          entryFileNames: `[name].js`,
          chunkFileNames: `[name].js`,
          assetFileNames: `[name].[ext]`,
        },
      },
    },
    plugins: [react(), tsconfigPaths()],
    server: {
      proxy,
      host: "0.0.0.0",
      port: 3000,
      headers: resHeaders,
      open: true,
    },
  });
};
