plugins {
    base
}

val ktlint by configurations.creating

dependencies {
    ktlint(versionCatalogs.named("libs").findLibrary("ktlint").get())
}

val outputDir = project.layout.buildDirectory.dir("reports/ktlint/")
val inputFiles = fileTree("src") { include("**/*.kt") }
val editorconfig = rootProject.file(".editorconfig").absolutePath

tasks {
    val ktlintRun by registering(JavaExec::class) {
        group = "verification"
        inputs.files(inputFiles)
        outputs.dir(outputDir)
        mainClass.set("com.pinterest.ktlint.Main")
        classpath = ktlint
        args = listOf("--editorconfig=$editorconfig", "src/**/*.kt")
        jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }

    val ktlintFormat by registering(JavaExec::class) {
        group = "verification"
        inputs.files(inputFiles)
        outputs.dir(outputDir)
        mainClass.set("com.pinterest.ktlint.Main")
        classpath = ktlint
        args = listOf("--editorconfig=$editorconfig", "-F", "src/**/*.kt")
        jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    }

    check { dependsOn(ktlintRun) }
    assemble { dependsOn(ktlintFormat) }
}
