import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentFilter
import com.github.benmanes.gradle.versions.updates.resolutionstrategy.ComponentSelectionWithCurrent
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
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
    androidLibrary {
        namespace = "dev.jfronny.zerointerest.composeapp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true

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
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.browser)
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
