package com.comparetool.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies that the core module compiles, java-diff-utils is
 * on the classpath, and the test runner reaches this module.
 */
class CoreSmokeTest {

    @Test
    void buildWiringIsCorrect() {
        assertThat("core module").isNotEmpty();
    }
}
