import de.undercouch.gradle.tasks.download.Download
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

plugins {
    kotlin("jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.download)
}

val computedVersionName: String by rootProject.extra
val computedVersionCode: Int by rootProject.extra

group = "dev.jfronny.zerointerest"
version = computedVersionName

dependencies {
    implementation(projects.shared)
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }
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
            configurationFiles.from(
                "proguard-desktop-rules.pro",
                project(":shared").projectDir.resolve("proguard-rules.pro"),
            )
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
                rename { "$appName-x86_64.msi" }
            }
        }
    }

    configurePackageTasks("")
    configurePackageTasks("Release")
}
