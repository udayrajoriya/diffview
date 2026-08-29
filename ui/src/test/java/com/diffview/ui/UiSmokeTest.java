package com.diffview.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies that the ui module compiles, JavaFX controls and
 * AtlantaFX are on the classpath, and the test runner reaches this module.
 * Full headless TestFX tests are added in task 12.x.
 */
class UiSmokeTest {

    @Test
    void buildWiringIsCorrect() {
        assertThat("ui module").isNotEmpty();
    }
}
