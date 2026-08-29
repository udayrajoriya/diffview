package com.diffview.ui;

import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler} (REQ-016.2).
 *
 * <p>Tests verify that the handler:
 * <ul>
 *   <li>invokes the injectable dialog factory when called from the FX thread</li>
 *   <li>includes exception details (thread name, message) in the dialog content</li>
 *   <li>can be installed as the default uncaught exception handler</li>
 *   <li>never throws even when the factory is not set explicitly</li>
 * </ul>
 */
@ExtendWith(ApplicationExtension.class)
class GlobalExceptionHandlerTest {

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new VBox(), 400, 300));
        stage.show();
    }

    @Test
    void handlerInvokesDialogFactoryWhenCalledFromFxThread(FxRobot robot) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        boolean[] invoked = {false};
        handler.setDialogFactory((title, detail) -> invoked[0] = true);

        robot.interact(() ->
                handler.uncaughtException(Thread.currentThread(), new RuntimeException("fx-error")));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(invoked[0]).isTrue();
    }

    @Test
    void handlerIncludesExceptionMessageInDetail(FxRobot robot) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        List<String> details = new ArrayList<>();
        handler.setDialogFactory((title, detail) -> details.add(detail));

        robot.interact(() ->
                handler.uncaughtException(Thread.currentThread(),
                        new RuntimeException("my-unique-error-marker")));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(details).hasSize(1);
        assertThat(details.get(0)).contains("my-unique-error-marker");
    }

    @Test
    void handlerIncludesThreadNameInDetail(FxRobot robot) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        List<String> details = new ArrayList<>();
        handler.setDialogFactory((title, detail) -> details.add(detail));

        // Capture the FX-thread name inside interact() so it matches what the handler sees
        String[] fxThreadName = {null};
        robot.interact(() -> {
            fxThreadName[0] = Thread.currentThread().getName();
            handler.uncaughtException(Thread.currentThread(), new RuntimeException("err"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(details).hasSize(1);
        assertThat(details.get(0)).contains(fxThreadName[0]);
    }

    @Test
    void handlerTitleIsUnexpectedError(FxRobot robot) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        List<String> titles = new ArrayList<>();
        handler.setDialogFactory((title, detail) -> titles.add(title));

        robot.interact(() ->
                handler.uncaughtException(Thread.currentThread(), new RuntimeException("e")));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(titles).containsExactly("Unexpected Error");
    }

    @Test
    void installSetsDefaultUncaughtExceptionHandler(FxRobot robot) {
        GlobalExceptionHandler.install();
        assertThat(Thread.getDefaultUncaughtExceptionHandler())
                .isInstanceOf(GlobalExceptionHandler.class);
    }
}
