// viewmodel — MVVM ViewModels using JavaFX observable properties.
// Needs javafx.base (ObjectProperty, IntegerProperty, BooleanProperty, etc.)
plugins {
    alias(libs.plugins.javafx)
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.base")
}

dependencies {
    implementation(project(":model"))
    implementation(project(":infra"))
    implementation(project(":core"))

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
}
