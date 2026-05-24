@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val computedVersionName: String by rootProject.extra
val computedVersionCode: Int by rootProject.extra

group = "dev.jfronny.zerointerest"
version = computedVersionName

kotlin {
    js {
        browser()
        binaries.executable()
        useEsModules()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
        useEsModules()
    }

    sourceSets {
        webMain.dependencies {
            implementation(projects.shared)
            implementation(npm("copy-webpack-plugin", libs.versions.copyWebpackPlugin.get()))
            implementation(npm("@js-joda/timezone", libs.versions.jsJoda.get()))
            implementation(npm("sqlite-web-worker", layout.projectDirectory.dir("sqlite-web-worker").asFile))
        }
    }
}
