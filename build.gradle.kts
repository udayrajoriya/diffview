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
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    // Java 17 does not default file.encoding to UTF-8 (that's JEP 400, JDK 18+), so on a
    // non-UTF-8 platform default (e.g. windows-1252) javac would silently corrupt the
    // non-ASCII characters (arrows, box-drawing) used in source string literals and comments.
    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
        }
    }
}
