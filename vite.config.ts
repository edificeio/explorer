import { defineConfig, loadEnv } from "vite";
import path from "path";
import react from "@vitejs/plugin-react";

// https://vitejs.dev/config/
export default ({mode}: {mode:string}) => {
  // Checking environement files
  const envFile = loadEnv(mode, process.cwd());
  const envs = {...process.env, ...envFile};  
  const hasEnvFile = Object.keys(envFile).length;

  // Proxy variables
  const headers = { cookie: `oneSessionId=${envs.VITE_RD}` };

  const proxyObj = hasEnvFile ? {
    target: "https://rd.opendigitaleducation.com",
    changeOrigin: true, headers
  } : {
    target: "http://localhost:8090",
    changeOrigin: false
  }

  /* If we need different endpoint prod/dev mode */
  /* const proxyconf = () => {
    if (mode === "production") {
      return {
        target: "http://localhost:8090",
        changeOrigin: false
      }
    } else {
      return {
        target: "https://rd.opendigitaleducation.com",
        changeOrigin: true, headers
      }
    }
  } */

  return defineConfig({
    resolve: {
      alias: [
        { find: "~", replacement: path.resolve(__dirname, "src") },
        {
          find: "@components",
          replacement: path.resolve(__dirname, "./src/components"),
        },
        {
          find: "@contexts",
          replacement: path.resolve(__dirname, "./src/contexts"),
        },
        {
          find: "@features",
          replacement: path.resolve(__dirname, "./src/features"),
        },
        {
          find: "@hooks",
          replacement: path.resolve(__dirname, "./src/hooks"),
        },
        {
          find: "@pages",
          replacement: path.resolve(__dirname, "./src/pages"),
        },
        {
          find: "@shared",
          replacement: path.resolve(__dirname, "./src/shared"),
        },
      ],
    },
    build: {
      manifest: true,
      assetsDir: "assets/js/ode-explorer/",
      rollupOptions: {
        output: {
          entryFileNames: `[name].js`,
          chunkFileNames: `[name].js`,
          assetFileNames: `[name].[ext]`,
        },
      },
    },
    plugins: [react()],
    server: {
      proxy: {
        // List of all applications
        "/applications-list": proxyObj,
        // Public Conf
        "/conf/public": proxyObj,
        "^/(?=assets|theme|locale|i18n|skin)": proxyObj,
        // Entcore urls
        "^/(?=auth|cas|userbook|directory|communication|conversation|portal|session|timeline|workspace)":
        proxyObj,
        // App urls
        "/blog": proxyObj,
        "/explorer": proxyObj,
      },
      host: "0.0.0.0",
      port: 3000,
      //open: true,
    },
  });
}
