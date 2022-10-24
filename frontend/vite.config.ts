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
    /* proxy: {
      "/blog": {
        target: "http://localhost:8090",
        changeOrigin: false,
      },
    }, */
    host: "0.0.0.0",
    port: 3000,
    open: true,
  },
});
