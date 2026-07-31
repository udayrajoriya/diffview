// core — diff engines, merge manager, comparison service facade.
// No JavaFX (REQ-017 module boundary).
dependencies {
    implementation(project(":model"))
    implementation(project(":infra"))

    implementation(libs.diffutils)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
}
