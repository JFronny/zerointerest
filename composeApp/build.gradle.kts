@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.android.build.api.withAndroid
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentFilter
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmRun

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.gradleVersions)
//    alias(libs.plugins.kotest) // temporarily disabled: breaks kotlin
    alias(libs.plugins.download)
}

val computedVersionName: String by rootProject.extra
val computedVersionCode: Int by rootProject.extra

group = "dev.jfronny.zerointerest"
version = computedVersionName

repositories {
    mavenCentral()
    google()
}

kotlin {
    android {
        namespace = "dev.jfronny.zerointerest.composeapp"
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
        @OptIn(ExperimentalKotlinGradlePluginApi::class, InternalKotlinGradlePluginApi::class)
        mainRun {
            @Suppress("UNCHECKED_CAST") val provider = javaClass.getMethod("getTask")(this) as TaskProvider<KotlinJvmRun>
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
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.trixnity.client)
            implementation(libs.trixnity.client.cryptodriver.vodozemac)
            implementation(libs.ktor.client.core)
            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlin.logging)
            implementation(kotlin("reflect"))
            implementation(libs.androidx.room.runtime)
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
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.java)
            implementation(libs.slf4j.over.jpl)
            implementation(libs.commons.logger)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
            implementation(libs.koin.android)
            implementation(libs.koin.android.compat)
            implementation(libs.ktor.client.android)
            implementation(libs.slf4j.android)
        }
        webMain.dependencies {
            implementation(npm("copy-webpack-plugin", libs.versions.copyWebpackPlugin.get()))
            implementation(libs.androidx.sqlite.web)
            implementation(npm("sqlite-web-worker", layout.projectDirectory.dir("sqlite-web-worker").asFile))
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
    ksp(libs.androidx.room.compiler)
}

room3 {
    schemaDirectory(layout.projectDirectory.dir("schemas"))
}

compose.desktop {
    application {
        mainClass = "dev.jfronny.zerointerest.MainKt"

        nativeDistributions {
            packageName = "zerointerest"
            packageVersion = computedVersionName.substringBefore('+')
            description = "Simple money lending"
            vendor = "JFronny"

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage)
            modules("java.net.http", "jdk.unsupported")
            jvmArgs("--enable-native-access=ALL-UNNAMED")

            windows {
                iconFile = rootProject.file("icon.ico")
                perUserInstall = true
                console = false
            }
            linux {
                packageVersion = computedVersionName
                iconFile = rootProject.file("icon.png")
            }
        }

        buildTypes.release.proguard {
            version = libs.versions.proguard
            configurationFiles.from("proguard-rules.pro", "proguard-desktop-rules.pro")
            optimize = false
        }
    }
}

val appImageTool = layout.buildDirectory.file("tmp/appimagetool-x86_64.AppImage")
val downloadAppImageTool by tasks.registering(Download::class) {
    notCompatibleWithConfigurationCache("Uses build script variable as target for simplicity")
    src("https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage")
    dest(appImageTool)
    overwrite(true)
    onlyIfModified(false)
    doLast {
        appImageTool.get().asFile.setExecutable(true)
    }
}

afterEvaluate {
    val packageTasks = tasks.withType(AbstractJPackageTask::class)
    val appDirSrc = project.file("appimage")

    fun configurePackageTasks(type: String) {
        packageTasks.findByName("package${type}AppImage")?.let {
            val files = it.outputs.files.files
            require(files.size == 1) { "Expected exactly one file, got ${files.size}" }
            val appName = it.packageName.get()
            val packageOutput = files.first().resolve(appName)
            val kind = files.first().parentFile.name
            val appDir = layout.buildDirectory.dir("appimage/$kind/$appName.AppDir")
            val finalOutput = layout.buildDirectory.file("appimage/$kind/$appName-x86_64.AppImage")

            val prepareAppImage = tasks.register("prepare${type}AppImage", Copy::class) {
                dependsOn(it)
                from(appDirSrc)
                from(packageOutput)
                into(appDir)
                exclude { it.path.contains("/legal/") }
                doLast {
                    appDir.get().asFile.let {
                        it.resolve("lib/zerointerest.png").copyTo(it.resolve("zerointerest.png"), overwrite = true)
                    }
                }
            }

            val buildAppImage = tasks.register("build${type}AppImage", Exec::class) {
                dependsOn(downloadAppImageTool, prepareAppImage)
                workingDir = layout.buildDirectory.dir("appimage").get().asFile
                commandLine(
                    appImageTool.get().asFile.absolutePath,
                    appDir.get().asFile.absolutePath,
                    finalOutput.get().asFile.absolutePath,
                )
                environment("ARCH", "x86_64")
                outputs.file(finalOutput)
            }
        }

        packageTasks.findByName("package${type}Msi")?.let {
            val appName = it.packageName.get()

            val moveMsi = tasks.register("move${type}Msi", Copy::class) {
                dependsOn(it)
                from(it.outputs.files.files.first())
                into(layout.buildDirectory.dir("msi"))
                rename { "$appName.msi" }
            }
        }
    }

    configurePackageTasks("")
    configurePackageTasks("Release")
}

open class UpgradeToUnstableFilter : ComponentFilter {
    override fun reject(candidate: ComponentSelectionWithCurrent) = reject(candidate.currentVersion, candidate.candidate.version)

    open fun reject(old: String, new: String): Boolean {
        return !isStable(new) && isStable(old) // no unstable proposals for stable dependencies
    }

    open fun isStable(version: String): Boolean {
        val stableKeyword = setOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
        val stablePattern = version.matches(Regex("""^[0-9,.v-]+(-r)?$"""))
        return stableKeyword || stablePattern
    }
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf(UpgradeToUnstableFilter())
}

tasks.withType<Test> {
    useJUnitPlatform()
}
