@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.google.devtools.ksp.gradle.KspAATask
import com.skydoves.compose.stability.gradle.StabilityCheckTask
import de.undercouch.gradle.tasks.download.Download
import dev.jfronny.zerointerest.ConvertExchangeRatesTask
import dev.jfronny.zerointerest.UpgradeToUnstableFilter
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
//    alias(libs.plugins.kotest) // temporarily disabled: breaks kotlin
    alias(libs.plugins.download)
    alias(libs.plugins.stability.analyzer)
    ktlint
}

val computedVersionName: String = rootProject.extra["computedVersionName"] as String
val computedVersionCode: Int = rootProject.extra["computedVersionCode"] as Int

group = "dev.jfronny.zerointerest"
version = computedVersionName

repositories {
    mavenCentral()
    google()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    android {
        namespace = "dev.jfronny.zerointerest.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        buildToolsVersion = libs.versions.android.buildTools.get()
        androidResources.enable = true

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_25
        }
        @OptIn(ExperimentalKotlinGradlePluginApi::class, InternalKotlinGradlePluginApi::class)
        mainRun {
            @Suppress("UNCHECKED_CAST")
            val provider = javaClass.getMethod("getTask")(this) as TaskProvider<KotlinJvmRun>
            provider.configure {
                jvmArgs("--enable-native-access=ALL-UNNAMED")
            }
        }
    }

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

    applyHierarchyTemplate {
        common {
            group("nonJs") {
                withJvm()
                withAndroid()
                group("apple") {
                    group("ios") {
                        withIos()
                    }
                }
            }

            group("web") {
                withJs()
                withWasmJs()
            }
        }
    }

    sourceSets {
        val nonJsMain = named("nonJsMain")
        val nonJsTest = named("nonJsTest")

        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.preview)
            implementation(libs.compose.material3AdaptiveNavigationSuite)
            implementation(libs.androidx.navigation3.ui)
            implementation(libs.androidx.navigation3.runtime)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            api(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            api(libs.trixnity.client)
            implementation(libs.trixnity.client.cryptodriver.vodozemac)
            implementation(libs.ktor.client.core)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            api(libs.kotlin.logging)
            implementation(kotlin("reflect"))
            api(libs.androidx.room.runtime)
            implementation(libs.compose.emojikt)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.coil.svg)
        }
        commonTest.dependencies {
            implementation(libs.kotest.framework.engine)
            implementation(libs.kotest.assertions.core)
            implementation(libs.kotest.property)
            implementation(libs.kotest.extensions.koin)
            implementation(libs.koin.test)
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            api(compose.desktop.currentOs)
            api(libs.kotlinx.coroutines.swing)
            api(libs.ktor.client.java)
            api(libs.slf4j.over.jpl)
            api(libs.commons.logger)
        }
        androidMain.dependencies {
            api(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            implementation(libs.koin.android)
            implementation(libs.koin.android.compat)
            implementation(libs.ktor.client.android)
            implementation(libs.slf4j.android)
        }
        webMain.dependencies {
            implementation(libs.androidx.sqlite.web)
            implementation(libs.trixnity.client.repository.indexeddb)
            implementation(libs.trixnity.client.media.indexeddb)
            implementation(libs.indexeddb)
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
    androidRuntimeClasspath(libs.compose.uiTooling)
    val configurations = listOf(
        "kspAndroid",
        "kspAndroidMain",
        "kspCommonMainMetadata",
        "kspIosArm64",
        "kspIosArm64Test",
        "kspIosSimulatorArm64",
        "kspIosSimulatorArm64Test",
        "kspJs",
        "kspJsTest",
        "kspJvm",
        "kspJvmTest",
        "kspWasmJs",
        "kspWasmJsTest",
    )
    require(project.configurations.map { it.name }
        .filter { it != "ksp" && it.startsWith("ksp") }.toSet() == configurations.toSet()) {
        "Unexpected KSP configuration"
    }
    configurations.forEach { it(libs.androidx.room.compiler) }
}

room3 {
    schemaDirectory(layout.projectDirectory.dir("schemas"))
}

composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file("stability_config.conf"))
}

composeStabilityAnalyzer {
    stabilityValidation {
        stabilityConfigurationFiles.add(layout.projectDirectory.file("stability_config.conf"))
    }
}

tasks {
    withType<DependencyUpdatesTask> { rejectVersionIf(UpgradeToUnstableFilter()) }
    withType<Test> { useJUnitPlatform() }
    withType<KotlinNativeSimulatorTest> { enabled = false }
    "wasmJsBrowserTest" { enabled = false }
    "jsBrowserTest" { enabled = false }
    withType<AbstractKotlinCompile<*>> { dependsOn(ktlintFormat) }
    withType<KspAATask> { dependsOn(ktlintFormat) }
    withType<StabilityCheckTask> { enabled = false }
}

val downloadEcbExchangeRates = tasks.register("downloadEcbExchangeRates", Download::class) {
    src("https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml")
    dest(layout.buildDirectory.file("tmp/exchange-rates.xml"))
    overwrite(true)
    onlyIfModified(false)
}

val downloadFrankfurterExchangeRates = tasks.register("downloadFrankfurterExchangeRates", Download::class) {
    src("https://api.frankfurter.dev/v2/rates?base=EUR")
    dest(layout.buildDirectory.file("tmp/exchange-rates.json"))
    overwrite(true)
    onlyIfModified(false)
}

val downloadSymbolMap = tasks.register("downloadSymbolMap", Download::class) {
    src("https://raw.githubusercontent.com/bengourley/currency-symbol-map/refs/heads/master/map.js")
    dest(layout.buildDirectory.file("tmp/currency-symbol-map.js"))
    overwrite(true)
    onlyIfModified(false)
}

val convertExchangeRates = tasks.register("convertExchangeRates", ConvertExchangeRatesTask::class) {
    dependsOn(downloadEcbExchangeRates, downloadFrankfurterExchangeRates, downloadSymbolMap)
    ecbExchangeRatesFile = downloadEcbExchangeRates.map { it.dest }
    frankfurterExchangeRatesFile = downloadFrankfurterExchangeRates.map { it.dest }
    symbolMapFile = downloadSymbolMap.map { it.dest }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir(convertExchangeRates)
}
