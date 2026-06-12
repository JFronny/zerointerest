import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    ktlint
}

val computedVersionName: String by rootProject.extra
val computedVersionCode: Int by rootProject.extra

group = "dev.jfronny.zerointerest"
version = computedVersionName

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

base {
    archivesName = "zerointerest"
}

android {
    namespace = "dev.jfronny.zerointerest"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = libs.versions.android.buildTools.get()

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
        val release = getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                project(":shared").projectDir.resolve("proguard-rules.pro"),
            )
        }
        register("unsigned") {
            initWith(release)
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".unsigned"
            versionNameSuffix = "-unsigned"
            installation {
                enableBaselineProfile = false
            }
        }
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.shared) {
        val uiTooling = libs.compose.uiTooling.get()
        exclude(group = uiTooling.module.group, module = uiTooling.module.name)
    }

    debugImplementation(libs.compose.uiTooling)
}

tasks {
    withType<AbstractKotlinCompile<*>> { dependsOn(ktlintFormat) }
}
