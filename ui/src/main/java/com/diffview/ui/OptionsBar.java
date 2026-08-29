package com.diffview.ui;

import com.diffview.model.ComparisonOptions;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;

import java.nio.charset.Charset;
import java.util.List;
import java.util.function.Consumer;

/**
 * Toolbar containing per-pane encoding overrides and comparison ignore-option
 * toggles (task 12.5 — REQ-006).
 *
 * <h3>Controls</h3>
 * <ul>
 *   <li>{@code leftEncodingCombo} — encoding override for the left file.</li>
 *   <li>{@code rightEncodingCombo} — encoding override for the right file.</li>
 *   <li>{@code ignoreWhitespaceCheck} — ignore leading/trailing/intra-token whitespace.</li>
 *   <li>{@code ignoreLineEndingsCheck} — treat CR, LF, and CRLF as equivalent.</li>
 *   <li>{@code ignoreCaseCheck} — ignore letter-case differences.</li>
 * </ul>
 *
 * <p>Each change fires the {@link #setOnOptionsChanged(Consumer) onOptionsChanged} callback
 * with a fully-built {@link ComparisonOptions} snapshot.  Callers should wire this
 * to {@code vm.recompare(options)}.
 *
 * <p>Use {@link #setLeftEncodingDisplay(String)} / {@link #setRightEncodingDisplay(String)}
 * to reflect the encoding detected after a compare without triggering a re-compare.
 */
public class OptionsBar extends HBox {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Sentinel value meaning "no override — auto-detect the encoding". */
    static final String AUTO_DETECT = "Auto-detect";

    /** Ordered list of charset names shown in both combo boxes. */
    static final List<String> ENCODINGS = List.of(
            AUTO_DETECT,
            "UTF-8",
            "ISO-8859-1",
            "US-ASCII",
            "UTF-16",
            "UTF-16LE",
            "UTF-16BE",
            "windows-1252");

    // ── Controls ──────────────────────────────────────────────────────────────

    private final ComboBox<String> leftEncodingCombo  = new ComboBox<>();
    private final ComboBox<String> rightEncodingCombo = new ComboBox<>();
    private final CheckBox ignoreWhitespaceCheck      = new CheckBox("Ignore Whitespace");
    private final CheckBox ignoreLineEndingsCheck     = new CheckBox("Ignore Line Endings");
    private final CheckBox ignoreCaseCheck            = new CheckBox("Ignore Case");

    // ── State ─────────────────────────────────────────────────────────────────

    private Consumer<ComparisonOptions> onOptionsChanged;
    /** Guard flag: set to {@code true} while updating controls programmatically
     *  so that the change listener does not fire a re-compare callback. */
    private boolean suppressFire = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public OptionsBar() {
        // Left encoding combo
        leftEncodingCombo.setId("leftEncodingCombo");
        leftEncodingCombo.getItems().addAll(ENCODINGS);
        leftEncodingCombo.setValue(AUTO_DETECT);
        leftEncodingCombo.setPrefWidth(130);

        // Right encoding combo
        rightEncodingCombo.setId("rightEncodingCombo");
        rightEncodingCombo.getItems().addAll(ENCODINGS);
        rightEncodingCombo.setValue(AUTO_DETECT);
        rightEncodingCombo.setPrefWidth(130);

        // Checkboxes
        ignoreWhitespaceCheck.setId("ignoreWhitespaceCheck");
        ignoreLineEndingsCheck.setId("ignoreLineEndingsCheck");
        ignoreCaseCheck.setId("ignoreCaseCheck");

        // Wire change listeners
        leftEncodingCombo .valueProperty().addListener((obs, o, n) -> fireIfAllowed());
        rightEncodingCombo.valueProperty().addListener((obs, o, n) -> fireIfAllowed());
        ignoreWhitespaceCheck .selectedProperty().addListener((obs, o, n) -> fireIfAllowed());
        ignoreLineEndingsCheck.selectedProperty().addListener((obs, o, n) -> fireIfAllowed());
        ignoreCaseCheck       .selectedProperty().addListener((obs, o, n) -> fireIfAllowed());

        // Layout
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(4, 8, 4, 8));
        setSpacing(8);

        getChildren().addAll(
                new Label("L:"),  leftEncodingCombo,
                new Separator(Orientation.VERTICAL),
                new Label("R:"),  rightEncodingCombo,
                new Separator(Orientation.VERTICAL),
                ignoreWhitespaceCheck,
                ignoreLineEndingsCheck,
                ignoreCaseCheck);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers the callback to be invoked whenever any option changes.
     * Pass {@code null} to remove the callback.
     *
     * @param callback receives a snapshot of the current {@link ComparisonOptions}
     */
    public void setOnOptionsChanged(Consumer<ComparisonOptions> callback) {
        this.onOptionsChanged = callback;
    }

    /**
     * Builds a {@link ComparisonOptions} from the current control state.
     * The large-file warn threshold is taken from {@link ComparisonOptions#defaults()}.
     */
    public ComparisonOptions getOptions() {
        return ComparisonOptions.defaults()
                .withIgnoreWhitespace(ignoreWhitespaceCheck.isSelected())
                .withIgnoreLineEndings(ignoreLineEndingsCheck.isSelected())
                .withIgnoreCase(ignoreCaseCheck.isSelected())
                .withLeftEncodingOverride(toCharset(leftEncodingCombo.getValue()))
                .withRightEncodingOverride(toCharset(rightEncodingCombo.getValue()));
    }

    /**
     * Sets the left encoding combo to {@code charsetName} <em>without</em> firing
     * the {@code onOptionsChanged} callback.  Typically called after a compare
     * completes to reflect the auto-detected encoding.
     *
     * <p>If {@code charsetName} is not already in the combo list it is added.
     *
     * @param charsetName charset name to display, e.g. {@code "UTF-8"}
     */
    public void setLeftEncodingDisplay(String charsetName) {
        if (charsetName == null) return;
        suppressFire = true;
        try {
            addIfAbsent(leftEncodingCombo, charsetName);
            leftEncodingCombo.setValue(charsetName);
        } finally {
            suppressFire = false;
        }
    }

    /**
     * Sets the right encoding combo to {@code charsetName} <em>without</em> firing
     * the {@code onOptionsChanged} callback.
     *
     * @param charsetName charset name to display, e.g. {@code "ISO-8859-1"}
     */
    public void setRightEncodingDisplay(String charsetName) {
        if (charsetName == null) return;
        suppressFire = true;
        try {
            addIfAbsent(rightEncodingCombo, charsetName);
            rightEncodingCombo.setValue(charsetName);
        } finally {
            suppressFire = false;
        }
    }

    // ── Package-private accessors (for tests) ─────────────────────────────────

    ComboBox<String> getLeftEncodingCombo()   { return leftEncodingCombo; }
    ComboBox<String> getRightEncodingCombo()  { return rightEncodingCombo; }
    CheckBox getIgnoreWhitespaceCheck()       { return ignoreWhitespaceCheck; }
    CheckBox getIgnoreLineEndingsCheck()      { return ignoreLineEndingsCheck; }
    CheckBox getIgnoreCaseCheck()             { return ignoreCaseCheck; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void fireIfAllowed() {
        if (!suppressFire && onOptionsChanged != null) {
            onOptionsChanged.accept(getOptions());
        }
    }

    /**
     * Converts a combo-box string value to a {@link Charset}, or {@code null}
     * for {@link #AUTO_DETECT} or unrecognised names.
     */
    private static Charset toCharset(String name) {
        if (name == null || AUTO_DETECT.equals(name)) return null;
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static void addIfAbsent(ComboBox<String> combo, String value) {
        if (!combo.getItems().contains(value)) {
            combo.getItems().add(value);
        }
    }
}
