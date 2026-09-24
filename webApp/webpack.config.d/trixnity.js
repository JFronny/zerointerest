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
    config.optimization = {
        minimize: true,
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
        "Access-Control-Allow-Headers": "content-type",
    }
}
