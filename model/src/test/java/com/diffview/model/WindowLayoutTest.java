package com.diffview.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WindowLayoutTest {

    @Nested
    class Defaults {

        @Test
        void defaultsHave1280x800() {
            WindowLayout layout = WindowLayout.defaults();
            assertThat(layout.width()).isEqualTo(1280.0);
            assertThat(layout.height()).isEqualTo(800.0);
        }

        @Test
        void defaultsNotMaximized() {
            assertThat(WindowLayout.defaults().maximized()).isFalse();
        }

        @Test
        void defaultsHaveEqualSplit() {
            assertThat(WindowLayout.defaults().splitDividerRatio()).isEqualTo(0.5);
        }
    }

    @Nested
    class WitherMethods {

        @Test
        void withWidth_updatesWidth() {
            WindowLayout layout = WindowLayout.defaults().withWidth(1920);
            assertThat(layout.width()).isEqualTo(1920.0);
            assertThat(layout.height()).isEqualTo(800.0); // unchanged
        }

        @Test
        void withHeight_updatesHeight() {
            WindowLayout layout = WindowLayout.defaults().withHeight(1080);
            assertThat(layout.height()).isEqualTo(1080.0);
        }

        @Test
        void withMaximized_setsTrue() {
            assertThat(WindowLayout.defaults().withMaximized(true).maximized()).isTrue();
        }

        @Test
        void withSplitDividerRatio_setsValue() {
            WindowLayout layout = WindowLayout.defaults().withSplitDividerRatio(0.3);
            assertThat(layout.splitDividerRatio()).isEqualTo(0.3);
        }

        @Test
        void withXandY_setPosition() {
            WindowLayout layout = WindowLayout.defaults().withX(100).withY(200);
            assertThat(layout.x()).isEqualTo(100.0);
            assertThat(layout.y()).isEqualTo(200.0);
        }
    }

    @Nested
    class Validation {

        @Test
        void zeroWidthThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> WindowLayout.defaults().withWidth(0))
                    .withMessageContaining("width");
        }

        @Test
        void negativeHeightThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> WindowLayout.defaults().withHeight(-1))
                    .withMessageContaining("height");
        }

        @Test
        void splitRatioAboveOneThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> WindowLayout.defaults().withSplitDividerRatio(1.1))
                    .withMessageContaining("splitDividerRatio");
        }

        @Test
        void splitRatioBelowZeroThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> WindowLayout.defaults().withSplitDividerRatio(-0.1))
                    .withMessageContaining("splitDividerRatio");
        }

        @Test
        void splitRatioBoundaryValuesValid() {
            assertThatNoException().isThrownBy(() -> WindowLayout.defaults().withSplitDividerRatio(0.0));
            assertThatNoException().isThrownBy(() -> WindowLayout.defaults().withSplitDividerRatio(1.0));
        }
    }
}
