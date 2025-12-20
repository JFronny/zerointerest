plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

tasks {
    val pages by registering(Copy::class) {
        from(project(":composeApp").tasks["jsBrowserDistribution"])
        from(project(":composeApp").tasks["packageRelease"]) {
            rename { if (it.endsWith(".apk")) "android.apk" else it }
            exclude { !it.name.endsWith(".apk") && it.name != "output-metadata.json" }
        }
        into(projectDir.resolve("pages"))
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
