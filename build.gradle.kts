// Root build — common configuration shared across all subprojects.
// Module-specific dependencies are declared in each subproject's own build.gradle.kts.

subprojects {
    apply(plugin = "java")

    group = "com.diffview"
    version = "0.1.0"

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
