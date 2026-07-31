package com.comparetool.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies that the model module compiles, the build
 * wiring is correct, and test dependencies are resolvable.
 */
class ModelSmokeTest {

    @Test
    void buildWiringIsCorrect() {
        // Trivial assertion: proves the test runner reaches this module.
        assertThat("model module").isNotEmpty();
    }
}
