import { defineConfig } from "vite";
import path from "path";
import react from "@vitejs/plugin-react";

// https://vitejs.dev/config/
export default defineConfig({
  resolve: {
    alias: [
      { find: "~", replacement: path.resolve(__dirname, "src") },
      {
        find: "components",
        replacement: path.resolve(__dirname, "./src/components"),
      },
    ],
  },
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 8080,
    open: true
  },
});
