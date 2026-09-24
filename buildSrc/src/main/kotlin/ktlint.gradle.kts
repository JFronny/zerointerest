plugins {
    base
}

val ktlintConfig = configurations.create("ktlint")

dependencies {
    ktlintConfig(versionCatalogs.named("libs").findLibrary("ktlint").get()) {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
}

val outputDir = project.layout.buildDirectory.dir("reports/ktlint/")
val inputFiles = fileTree("src") { include("**/*.kt") }
val editorconfig = rootProject.file(".editorconfig").absolutePath

tasks {
    val ktlintRun = register("ktlintRun", JavaExec::class) {
        description = "Verify that the code is formatted"
        group = "verification"
        inputs.files(inputFiles)
        outputs.dir(outputDir)
        mainClass.set("com.pinterest.ktlint.Main")
        classpath = ktlintConfig
        args = listOf("--editorconfig=$editorconfig", "src/**/*.kt")
        jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }

    val ktlintFormat = register("ktlintFormat", JavaExec::class) {
        description = "Reformat kotlin source code"
        group = "verification"
        mustRunAfter(ktlintRun) // in CI, we actually want ktlintRun to cause failures with unformatted code
        inputs.files(inputFiles)
        outputs.dir(outputDir)
        mainClass.set("com.pinterest.ktlint.Main")
        classpath = ktlintConfig
        args = listOf("--editorconfig=$editorconfig", "-F", "src/**/*.kt")
        jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
    }

    check { dependsOn(ktlintRun) }
    assemble { dependsOn(ktlintFormat) }
}
