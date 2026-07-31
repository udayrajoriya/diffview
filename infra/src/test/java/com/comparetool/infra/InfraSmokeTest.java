package com.comparetool.infra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies that the infra module compiles, transitive
 * dependencies (Jackson, juniversalchardet) are resolvable, and the
 * test runner reaches this module.
 */
class InfraSmokeTest {

    @Test
    void buildWiringIsCorrect() {
        assertThat("infra module").isNotEmpty();
    }
}
