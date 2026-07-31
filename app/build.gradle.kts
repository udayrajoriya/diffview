// app — JavaFX bootstrap, DI wiring, entry point, jpackage target.
plugins {
    alias(libs.plugins.javafx)
    application
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls", "javafx.fxml")
}

application {
    mainClass.set("com.comparetool.app.MainApp")
}

// Strip -SNAPSHOT suffix: jpackage requires a numeric-only version (e.g. "1.0.0").
val releaseVersion: String = project.version.toString().substringBefore("-")

// ---------------------------------------------------------------------------
// packageAppImage — produces a self-contained app image using jpackage
// directly (no beryx.runtime / jlink pre-pass needed for non-modular apps).
//
// Run: ./gradlew packageAppImage   →  build/jpackage/ComparisonTool/
//
// For non-modular apps jpackage bundles the required JDK modules automatically
// via jdeps analysis; JavaFX jars (incl. platform natives) are bundled from
// the installDist classpath.
// ---------------------------------------------------------------------------
tasks.register<Delete>("cleanPackageAppImage") {
    group = "build"
    description = "Removes the previous jpackage app image output."
    delete(layout.buildDirectory.dir("jpackage"))
}

tasks.register<Exec>("packageAppImage") {
    group       = "distribution"
    description = "Produces a self-contained app-image using jpackage (REQ-17.2)."
    dependsOn("installDist", "cleanPackageAppImage")

    val osName       = System.getProperty("os.name").lowercase()
    val javaHome     = System.getProperty("java.home")
    val jpackageExt  = if (osName.contains("win")) ".exe" else ""
    val jpackageBin  = "$javaHome/bin/jpackage$jpackageExt"

    val installLibDir = layout.buildDirectory.dir("install/app/lib").get().asFile
    val outputDir     = layout.buildDirectory.dir("jpackage").get().asFile
    val mainJar       = "app-${project.version}.jar"

    // Packaging always re-runs (output is not tracked as Gradle up-to-date state).
    outputs.upToDateWhen { false }

    doFirst { outputDir.mkdirs() }

    commandLine(
        jpackageBin,
        "--type",        "app-image",
        "--name",        "ComparisonTool",
        "--app-version", releaseVersion,
        "--input",       installLibDir.absolutePath,
        "--main-jar",    mainJar,
        "--main-class",  "com.comparetool.app.MainApp",
        "--dest",        outputDir.absolutePath,
        // JavaFX jars in the app/ bundle are proper JPMS module jars.
        // The native jpackage launcher needs them on the module-path
        // (not just the classpath) so that JavaFX bootstraps correctly.
        "--java-options", "--module-path \$APPDIR",
        "--java-options", "--add-modules javafx.base,javafx.graphics,javafx.controls,javafx.fxml"
    )
}

// ---------------------------------------------------------------------------
// validatePackageImage — runs the packaged binary in --smoke-test mode to
// confirm the self-contained image starts and can perform basic comparisons.
//
// Run: ./gradlew validatePackageImage
// ---------------------------------------------------------------------------
tasks.register<Exec>("validatePackageImage") {
    group       = "verification"
    description = "Validates the packaged binary by running --smoke-test (REQ-17.2)."
    dependsOn("packageAppImage")

    val osName     = System.getProperty("os.name").lowercase()
    val imageDir   = layout.buildDirectory.dir("jpackage").get().asFile

    val binaryPath: String = when {
        osName.contains("win") ->
            File(imageDir, "ComparisonTool/ComparisonTool.exe").absolutePath
        osName.contains("mac") ->
            File(imageDir, "ComparisonTool.app/Contents/MacOS/ComparisonTool").absolutePath
        else ->
            File(imageDir, "ComparisonTool/bin/ComparisonTool").absolutePath
    }

    commandLine(binaryPath, "--smoke-test")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":infra"))
    implementation(project(":core"))
    implementation(project(":viewmodel"))
    implementation(project(":ui"))

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
}
