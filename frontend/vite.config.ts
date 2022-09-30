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
    ],
  },
  /* build: {
    assetsDir: "assets/js/explorer/",
  }, */
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 3000,
    open: true,
  },
});
