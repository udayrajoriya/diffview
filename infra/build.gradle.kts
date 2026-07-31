// infra — I/O, encoding detection, hashing, persistence.
// No JavaFX (REQ-017 module boundary: core/model free of JavaFX).
dependencies {
    implementation(project(":model"))

    implementation(libs.chardet)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
}
