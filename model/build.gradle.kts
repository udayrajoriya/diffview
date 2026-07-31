// model — pure Java records, enums, and value types.
// No JavaFX, no external dependencies (REQ-002, REQ-007, design module boundary).
dependencies {
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.assertj)
}
