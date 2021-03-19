var webpack = require('webpack');
var path = require('path');

//TODO à finir d'ADAPTER POUR UN BUILD LOCAL ou PLATEFORME (s'inspirer de webpack.config.watch.js)

module.exports = {
    context: path.resolve(__dirname, './src/main/resources/public/'),
    entry: {
        "explorer.agent": './ts/explorer.agent.ts'
    },
    output: {
        filename: './[name].js',
    },
    externals: {
        "ode-ts-client": 'window.entcore["ode-ts-client"]',
    },
    resolve: {
        modulesDirectories: ['node_modules'],
        extensions: ['', '.ts', '.js']
    },
    devtool: "source-map",
    module: {
        rules: [
            // ts-loader will handle files with `.ts` or `.tsx` extensions
            { test: /\.tsx?$/, loader: "ts-loader" },
        ]
    }
}
