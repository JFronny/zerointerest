import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 1
val versionBuild = System.getenv("CI_PIPELINE_IID")?.toInt() ?: 0

val computedVersionName = "$versionMajor.$versionMinor.$versionPatch+$versionBuild"

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

val computedVersionCode by lazy {
    var bits = 0
    bits = (bits shl 5) or versionMajor
    bits = (bits shl 7) or versionMinor
    bits = (bits shl 5) or versionPatch
    bits = (bits shl 14) or versionBuild
    bits
}

group = "dev.jfronny.zerointerest"
version = computedVersionName

repositories {
    maven("https://maven.frohnmeyer-wds.de/artifacts") {
        content {
            includeGroup("io.gitlab.jfronny")
        }
    }
    mavenCentral()
    google()
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
        }
    }

    js {
        browser()
        binaries.executable()
    }

//    @OptIn(ExperimentalWasmDsl::class)
//    wasmJs {
//        browser()
//        binaries.executable()
//    }
    
    applyDefaultHierarchyTemplate()

    sourceSets {
        val nonJsMain by creating { dependsOn(commonMain.get()) }
        jvmMain { dependsOn(nonJsMain) }
        androidMain { dependsOn(nonJsMain) }
        iosMain { dependsOn(nonJsMain) }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.trixnity.client)
            implementation(libs.ktor.client.core)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.java)
            implementation(libs.slf4j.over.jpl)
            implementation(libs.commons.logger)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.android.compat)
            implementation(libs.ktor.client.android)
            implementation(libs.slf4j.android)
        }
        jsMain.dependencies {
            implementation(libs.trixnity.client.repository.indexeddb)
            implementation(libs.trixnity.client.media.indexeddb)
        }
        webMain.dependencies {
            implementation(npm("copy-webpack-plugin", libs.versions.copyWebpackPlugin.get()))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        nonJsMain.dependencies {
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.trixnity.client.repository.room)
            implementation(libs.androidx.sqlite.bundled)
        }
    }

    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

dependencies {
    ksp(libs.androidx.room.compiler)
}

android {
    namespace = "dev.jfronny.zerointerest"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.jfronny.zerointerest"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = computedVersionCode
        versionName = computedVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

room {
    schemaDirectory(layout.projectDirectory.dir("schemas"))
}

compose.desktop {
    application {
        mainClass = "dev.jfronny.zerointerest.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.jfronny.zerointerest"
            packageVersion = computedVersionName.substringBefore('+')
        }
    }
}
