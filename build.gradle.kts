plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 1
val versionBuild = System.getenv("CI_PIPELINE_IID")?.toInt() ?: 0

val computedVersionName by extra {
    "$versionMajor.$versionMinor.$versionPatch+$versionBuild"
}

// Version code: S VVVVV MMMMMMM PPPPP IIIIIIIIIIIIII (32-bit integer)
// S (x1):  Sign bit. Must always be 0 for an android version code
// V (x5):  Major version. Up to 32, which should be enough (especially since we are still on 0)
//          This might not work for apps that follow proper semantic versioning, but who does that?
// M (x7):  Minor version. Up to 128, which should be enough
// P (x5):  Patch version. Up to 32, which should be enough for these
// I (x14): Pipeline ID bits. Allows a total of 16384 pipeline runs.
//          I'm simply guessing that that'll be enough
//
// This implementation assumes that these maximum numbers will never be reached.
// If they are reached, the version codes "bleed over" into the next range,
// so this should technically still produce valid, higher versions, but the format will be broken.

val computedVersionCode by extra {
    var bits = 0
    bits = (bits shl 5) or versionMajor
    bits = (bits shl 7) or versionMinor
    bits = (bits shl 5) or versionPatch
    bits = (bits shl 14) or versionBuild
    bits
}

tasks {
    val pages by registering(Copy::class) {
        from(project(":composeApp").tasks["jsBrowserDistribution"])
        from(project(":androidApp").tasks["packageRelease"]) {
            rename { if (it.endsWith(".apk")) "android.apk" else it }
            exclude { !it.name.endsWith(".apk") && it.name != "output-metadata.json" }
        }
        into(projectDir.resolve("pages"))
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}
