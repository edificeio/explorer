export const build = {
    manifest: true,
    assetsDir: "assets/js/ode-explorer/",
    rollupOptions: {
      output: {
        entryFileNames: `[name].js`,
        chunkFileNames: `[name].js`,
        assetFileNames: `[name].[ext]`,
      },
    },
}