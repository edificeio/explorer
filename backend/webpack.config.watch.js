const path = require('path');

module.exports = env => ({
  mode: "development",
  context: path.resolve(__dirname, './src/main/resources/public/'),
  entry: {
    "explorer.agent": './ts/explorer.agent.ts'
  },
  output: {
    filename: '[name].js',
    path: path.resolve(__dirname, 'dist', `${env.build_target}/mods/${env.build_app}/public/js`),
  },
  externals: {
    "ode-ts-client": 'window.entcore["ode-ts-client"]',
  },
  // @see https://github.com/TypeStrong/ts-loader#devtool--sourcemaps
  devtool: "inline-source-map",
  resolve: {
    // Resolvable extensions.
    extensions: [".ts", ".tsx", ".js"]
  },
  module: {
    rules: [
      // ts-loader will handle files with `.ts` or `.tsx` extensions
      { test: /\.tsx?$/, loader: "ts-loader", type: 'javascript/auto' },
    ],
  },
});
