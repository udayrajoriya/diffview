package com.comparetool.ui;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.core.diff.TextDiffEngine;
import com.comparetool.core.service.ComparisonService;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.infra.io.FileIOService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DecodedText;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.DiffModel;
import com.comparetool.model.DiffRow;
import com.comparetool.model.FileComparisonResult;
import com.comparetool.model.LineEnding;
import com.comparetool.model.LineKind;
import com.comparetool.viewmodel.FileComparisonViewModel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestFX integration tests for task 12.5 — encoding/whitespace option controls.
 *
 * <h3>Test coverage</h3>
 * <ul>
 *   <li>Initial combo state shows "Auto-detect" and no ignore flags</li>
 *   <li>Each ignore checkbox fires the onOptionsChanged callback with correct flag</li>
 *   <li>Encoding combo override fires callback with correct Charset</li>
 *   <li>Reverting to "Auto-detect" clears the encoding override to {@code null}</li>
 *   <li>{@code setLeftEncodingDisplay} / {@code setRightEncodingDisplay} do not fire callback</li>
 *   <li>After compare, combos reflect the detected encoding from the DiffModel</li>
 *   <li>Checking an ignore option triggers {@code vm.recompare()} via comparisonService</li>
 *   <li>Selecting an encoding override triggers recompare with the chosen Charset</li>
 * </ul>
 *
 * <p><strong>Extension order</strong>: {@code MockitoExtension} must precede
 * {@code ApplicationExtension} so that {@code @Mock} fields are injected before
 * {@code @Start} runs (see task 12.4 session notes).
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
class OptionsBarTest {

    private static final Path LEFT_PATH  = Path.of("/tmp/left.txt");
    private static final Path RIGHT_PATH = Path.of("/tmp/right.txt");

    @Mock ComparisonService comparisonService;
    @Mock FileIOService     fileIOService;

    private final TextDiffEngine     diffEngine = new LineDiffEngine();
    private final DirectTaskExecutor executor   = new DirectTaskExecutor();

    private FileComparisonViewModel vm;
    private FileComparisonView      view;
    private OptionsBar              optionsBar;

    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        vm         = new FileComparisonViewModel(comparisonService, diffEngine, executor, fileIOService);
        view       = new FileComparisonView();
        view.bindViewModel(vm);
        optionsBar = view.getOptionsBar();

        Scene scene = new Scene(view, 900, 600);
        stage.setScene(scene);
        stage.show();
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void initialOptionsHaveNoIgnoreFlags(FxRobot robot) {
        robot.interact(() -> {
            ComparisonOptions opts = optionsBar.getOptions();
            assertThat(opts.ignoreWhitespace()).isFalse();
            assertThat(opts.ignoreLineEndings()).isFalse();
            assertThat(opts.ignoreCase()).isFalse();
        });
    }

    @Test
    void initialEncodingOverridesAreNull(FxRobot robot) {
        robot.interact(() -> {
            ComparisonOptions opts = optionsBar.getOptions();
            assertThat(opts.leftEncodingOverride()).isNull();
            assertThat(opts.rightEncodingOverride()).isNull();
        });
    }

    @Test
    void initialCombosShowAutoDetect(FxRobot robot) {
        robot.interact(() -> {
            assertThat(optionsBar.getLeftEncodingCombo().getValue())
                    .isEqualTo(OptionsBar.AUTO_DETECT);
            assertThat(optionsBar.getRightEncodingCombo().getValue())
                    .isEqualTo(OptionsBar.AUTO_DETECT);
        });
    }

    // ── Checkbox callback tests ───────────────────────────────────────────────

    @Test
    void ignoreWhitespaceCheckboxFiresCallbackWithCorrectFlag(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.clickOn("#ignoreWhitespaceCheck");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).ignoreWhitespace()).isTrue();
    }

    @Test
    void ignoreCaseCheckboxFiresCallbackWithCorrectFlag(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.clickOn("#ignoreCaseCheck");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).ignoreCase()).isTrue();
    }

    @Test
    void ignoreLineEndingsCheckboxFiresCallbackWithCorrectFlag(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.clickOn("#ignoreLineEndingsCheck");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).ignoreLineEndings()).isTrue();
    }

    // ── Encoding override callback tests ──────────────────────────────────────

    @Test
    void leftEncodingOverrideFiresCallbackWithChosenCharset(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.interact(() -> optionsBar.getLeftEncodingCombo().setValue("UTF-16"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).leftEncodingOverride())
                .isEqualTo(Charset.forName("UTF-16"));
    }

    @Test
    void rightEncodingOverrideFiresCallbackWithChosenCharset(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.interact(() -> optionsBar.getRightEncodingCombo().setValue("ISO-8859-1"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).rightEncodingOverride())
                .isEqualTo(Charset.forName("ISO-8859-1"));
    }

    @Test
    void revertingToAutoDetectClearsLeftEncodingOverride(FxRobot robot) {
        // Override callback first so vm.recompare() is never triggered (no comparison loaded)
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        // Set an encoding
        robot.interact(() -> optionsBar.getLeftEncodingCombo().setValue("UTF-16"));
        received.clear(); // discard the UTF-16 callback, we only care about Auto-detect

        // Revert to Auto-detect
        robot.interact(() -> optionsBar.getLeftEncodingCombo().setValue(OptionsBar.AUTO_DETECT));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).hasSize(1);
        assertThat(received.get(0).leftEncodingOverride()).isNull();
    }

    // ── setXxxEncodingDisplay suppression tests ───────────────────────────────

    @Test
    void setLeftEncodingDisplayUpdatesComboWithoutFiringCallback(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.interact(() -> optionsBar.setLeftEncodingDisplay("ISO-8859-1"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).isEmpty();
        robot.interact(() ->
                assertThat(optionsBar.getLeftEncodingCombo().getValue())
                        .isEqualTo("ISO-8859-1"));
    }

    @Test
    void setRightEncodingDisplayUpdatesComboWithoutFiringCallback(FxRobot robot) {
        List<ComparisonOptions> received = new ArrayList<>();
        robot.interact(() -> optionsBar.setOnOptionsChanged(received::add));

        robot.interact(() -> optionsBar.setRightEncodingDisplay("UTF-16"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(received).isEmpty();
        robot.interact(() ->
                assertThat(optionsBar.getRightEncodingCombo().getValue())
                        .isEqualTo("UTF-16"));
    }

    // ── Integration: options changes trigger vm.recompare() ───────────────────

    @Test
    void encodingCombosReflectDetectedEncodingAfterCompare(FxRobot robot) {
        runCompare(robot);

        robot.interact(() -> {
            assertThat(optionsBar.getLeftEncodingCombo().getValue())
                    .isEqualTo(StandardCharsets.UTF_8.name());
            assertThat(optionsBar.getRightEncodingCombo().getValue())
                    .isEqualTo(StandardCharsets.UTF_8.name());
        });
    }

    @Test
    void checkingIgnoreWhitespaceTriggersRecompare(FxRobot robot) {
        runCompare(robot);
        Mockito.clearInvocations(comparisonService);

        robot.clickOn("#ignoreWhitespaceCheck");
        WaitForAsyncUtils.waitForFxEvents();

        ArgumentCaptor<ComparisonOptions> captor =
                ArgumentCaptor.forClass(ComparisonOptions.class);
        verify(comparisonService, atLeastOnce())
                .compareFiles(any(), any(), captor.capture(), any());
        assertThat(captor.getValue().ignoreWhitespace()).isTrue();
    }

    @Test
    void leftEncodingOverrideTriggersRecompareWithChosenCharset(FxRobot robot) {
        runCompare(robot);
        Mockito.clearInvocations(comparisonService);

        robot.interact(() -> optionsBar.getLeftEncodingCombo().setValue("UTF-16"));
        WaitForAsyncUtils.waitForFxEvents();

        ArgumentCaptor<ComparisonOptions> captor =
                ArgumentCaptor.forClass(ComparisonOptions.class);
        verify(comparisonService, atLeastOnce())
                .compareFiles(any(), any(), captor.capture(), any());
        assertThat(captor.getValue().leftEncodingOverride())
                .isEqualTo(Charset.forName("UTF-16"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Sets up mock responses and calls {@code vm.compare()} so tests start with
     * a live comparison. Produces 1 CHANGED block.
     */
    private void runCompare(FxRobot robot) {
        DecodedText leftDecoded  = new DecodedText(
                List.of("line1", "different"),
                StandardCharsets.UTF_8, false, LineEnding.LF);
        DecodedText rightDecoded = new DecodedText(
                List.of("line1", "changed"),
                StandardCharsets.UTF_8, false, LineEnding.LF);

        List<DiffRow>   rows   = List.of(
                DiffRow.unchanged(1, 1, "line1"),
                DiffRow.changed(2, 2, "different", "changed"));
        List<DiffBlock> blocks = List.of(new DiffBlock(1, 1, LineKind.CHANGED));
        DiffModel model = new DiffModel(rows, blocks,
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);

        FileComparisonResult result =
                new FileComparisonResult(model, LEFT_PATH, RIGHT_PATH);

        when(comparisonService.compareFiles(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(fileIOService.read(any(), any()))
                .thenAnswer(inv -> {
                    Path p = inv.getArgument(0);
                    return p.equals(LEFT_PATH) ? leftDecoded : rightDecoded;
                });

        robot.interact(() ->
                vm.compare(LEFT_PATH, RIGHT_PATH, ComparisonOptions.defaults()));
        WaitForAsyncUtils.waitForFxEvents();
    }
}
