plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlinxSerialization)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinpoet)

    implementation(libs.gradleVersions)
}
