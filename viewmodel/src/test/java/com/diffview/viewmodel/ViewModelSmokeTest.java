package com.comparetool.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test — verifies that the viewmodel module compiles, javafx.base
 * properties are accessible, and the test runner reaches this module.
 * JavaFX properties (ObjectProperty etc.) do not require the FX Application
 * thread, so no headless setup is needed here.
 */
class ViewModelSmokeTest {

    @Test
    void buildWiringIsCorrect() {
        assertThat("viewmodel module").isNotEmpty();
    }

    @Test
    void javafxBasePropertiesAreAccessible() {
        StringProperty prop = new SimpleStringProperty("hello");
        assertThat(prop.get()).isEqualTo("hello");
        prop.set("world");
        assertThat(prop.get()).isEqualTo("world");
    }
}
