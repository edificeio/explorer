import { defineConfig } from "vite";
import path from "path";
import react from "@vitejs/plugin-react";

// https://vitejs.dev/config/
export default defineConfig({
  resolve: {
    alias: [
      { find: "~", replacement: path.resolve(__dirname, "src") },
      {
        find: "@components",
        replacement: path.resolve(__dirname, "./src/components"),
      },
      {
        find: "@pages",
        replacement: path.resolve(__dirname, "./src/pages"),
      },
      {
        find: "@hooks",
        replacement: path.resolve(__dirname, "./src/hooks"),
      },
      {
        find: "@contexts",
        replacement: path.resolve(__dirname, "./src/contexts"),
      },
    ],
  },
  /* build: {
    assetsDir: "assets/js/explorer/",
  }, */
  plugins: [react()],
  /* server: {
    host: "0.0.0.0",
    port: 3000,
    open: true,
  }, */
  server: {
    proxy: {
      // Springboard urls
      "/conf/public": {
        target: "http://localhost:8090",
        changeOrigin: false,
      },
      "^/(?=assets|theme|locale|i18n|skin)": {
        target: "http://localhost:8090",
        changeOrigin: false,
      },
      // Entcore urls
      "^/(?=auth|cas|userbook|directory|communication|conversation|portal|session|timeline|workspace)":
        {
          target: "http://localhost:8090",
          changeOrigin: false,
        },
      // App urls
      "/blog": {
        target: "http://localhost:8090",
        changeOrigin: false,
      },
      "/explorer": {
        target: "http://localhost:8090",
        changeOrigin: false,
      },
    },
    host: "0.0.0.0",
    port: 3000,
    //open: true,
  },
});
