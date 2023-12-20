import { resolve } from "path";
import tsconfigPaths from "vite-tsconfig-paths";

import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import dts from "vite-plugin-dts";

import { dependencies } from "./package.json";

export default defineConfig({
  esbuild: {
    minifyIdentifiers: false,
  },
  build: {
    minify: false,
    outDir: "dist/lib",
    lib: {
      entry: {
        index: resolve(__dirname, "src/index.ts"),
      },
      formats: ["es"],
    },
    rollupOptions: {
      external: [...Object.keys(dependencies)],
    },
  },
  plugins: [
    react(),
    tsconfigPaths(),
    dts({
      outDir: "dist/lib",
    }),
  ],
});
