// ui — JavaFX views, controls, and AtlantaFX theming.
plugins {
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":infra"))
    implementation(project(":viewmodel"))

    // core and infra are needed in UI tests for DirectTaskExecutor,
    // LineDiffEngine, ComparisonService, and FileIOService
    testImplementation(project(":core"))
    testImplementation(project(":infra"))

    implementation(libs.atlantafx)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.hamcrest)
    testImplementation(libs.testfx.core)
    testImplementation(libs.testfx.junit5)
}

tasks.withType<Test> {
    // TestFX on Java 25 — module access needed for ApplicationExtension lifecycle
    jvmArgs(
        "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    )
}
