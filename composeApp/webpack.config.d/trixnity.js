config.resolve.alias = {
    crypto: false,
    fs: false,
    path: false,
    stream: false,
    buffer: false,
    os: false,
    url: false,
}

config.module.rules =
        [
            ...(config.module?.rules || []),
            {test: /\.wasm$/, type: "asset/resource"},
        ]

// Minification
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

// Dev Server
if (config.devServer) {
    config.devServer.headers = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET",
        "Access-Control-Allow-Headers": "content-type"
    }
}
