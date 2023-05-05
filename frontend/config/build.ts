/* import { dependencies } from "../package.json";

function renderChunks(deps: Record<string, string>) {
  const chunks = {};
  Object.keys(deps).forEach((key) => {
    if (["react", "react-dom"].includes(key)) return;
    chunks[key] = [key];
  });
  return chunks;
} */

export const build = {
  assetsDir: "assets/js/ode-explorer/",
  cssCodeSplit: false,
  rollupOptions: {
    output: {
      entryFileNames: `[name].js`,
      chunkFileNames: `[name].js`,
      assetFileNames: `[name].[ext]`,
      /* manualChunks: {
        vendor: ["react", "react-dom"],
        ...renderChunks(dependencies),
      }, */
    },
  },
};
