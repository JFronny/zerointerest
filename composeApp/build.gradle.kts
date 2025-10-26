import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinxSerialization)
}

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
            jvmTarget = JvmTarget.JVM_22
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

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.trixnity.client)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.trixnity.client.repository.room)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.ktor.client.java)
            implementation(libs.slf4j.over.jpl)
            implementation(libs.commons.logger)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.trixnity.client.repository.room)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.koin.android)
            implementation(libs.koin.android.compat)
            implementation(libs.ktor.client.android)
        }
        jsMain.dependencies {
            implementation(libs.trixnity.client.repository.indexeddb)
            implementation(libs.trixnity.client.media.indexeddb)
        }
        iosMain.dependencies {
            implementation(libs.trixnity.client.repository.room)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.trixnity.client.media.okio)
            implementation(libs.ktor.client.darwin)
        }
    }

    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(22)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

android {
    namespace = "dev.jfronny.zerointerest"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.jfronny.zerointerest"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
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

compose.desktop {
    application {
        mainClass = "dev.jfronny.zerointerest.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dev.jfronny.zerointerest"
            packageVersion = "1.0.0"
        }
    }
}
