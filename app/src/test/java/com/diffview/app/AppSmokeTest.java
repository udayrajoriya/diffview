package com.diffview.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies that the app module compiles, all module
 * dependencies are resolvable, and the test runner reaches this module.
 * End-to-end integration tests are added in task 17.1.
 */
class AppSmokeTest {

    @Test
    void buildWiringIsCorrect() {
        assertThat("app module").isNotEmpty();
    }
}
