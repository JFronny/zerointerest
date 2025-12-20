config.resolve.alias = {
    crypto: false,
    fs: false,
    path: false,
    stream: false,
    buffer: false,
    os: false,
    url: false,
}

if (config.mode === "production") {
    const TerserPlugin = require("terser-webpack-plugin");
    config.optimization = {
        minimize: true,
        minimizer: [
            new TerserPlugin({
                extractComments: true,
                terserOptions: {
                    compress: {
                        collapse_vars: false, // > 5 min
                        reduce_vars: false, // 30 secs
                    },
                    mangle: true,
                },
            }),
        ],
    };
} else {
    config.optimization = {
        minimize: false,
        minimizer: [],
    };
}

const CopyPlugin = require("copy-webpack-plugin");
config.plugins.push(
    new CopyPlugin({
        patterns: [
            {from: "../../node_modules/@matrix-org/olm/olm.wasm", to: "."},
        ],
    })
)
