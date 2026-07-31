// Root build — common configuration shared across all subprojects.
// Module-specific dependencies are declared in each subproject's own build.gradle.kts.

// Redirect build output to a temp directory outside OneDrive to avoid
// file-lock/sync issues with the VS Code Java Language Server.
val buildBase = File("C:/BuildTemp/GUIComparisonApp")

subprojects {
    layout.buildDirectory.set(File(buildBase, project.name))
    apply(plugin = "java")

    group = "com.comparetool"
    version = "1.0.0-SNAPSHOT"

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}
