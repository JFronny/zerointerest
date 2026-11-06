plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinxSerialization)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google {
        mavenContent {
            includeGroupAndSubgroups("androidx")
            includeGroupAndSubgroups("com.android")
            includeGroupAndSubgroups("com.google")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)

    implementation(libs.gradleVersions)
    implementation(libs.agp.settings.api)
}
