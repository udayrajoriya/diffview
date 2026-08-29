package com.diffview.ui;

import com.diffview.infra.persist.FilterProfile;
import com.diffview.model.FileMatchMode;
import com.diffview.model.FolderComparisonOptions;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Toolbar row for folder-comparison match criteria and mask filters (task 13.4).
 *
 * <p>Extended in task 14.1 with filter-profile export / import buttons (REQ-011.7,
 * REQ-014.5) so that users can save and reload frequently used include/exclude masks.
 *
 * <p>Covers:
 * <ul>
 *   <li>REQ-010 — match mode selector (size-only, size+timestamp, content) and timestamp
 *       tolerance field</li>
 *   <li>REQ-011 — include and exclude glob mask text fields</li>
 * </ul>
 *
 * <p>Callers register an {@link #setOnApply(Consumer)} callback that receives the
 * assembled {@link FolderComparisonOptions} whenever the user clicks "Apply".
 */
public class FolderOptionsBar extends HBox {

    // ── Controls ──────────────────────────────────────────────────────────────
    private final ComboBox<FileMatchMode> matchModeCombo    = new ComboBox<>();
    private final Label                   toleranceLabel    = new Label("Tolerance (s):");
    private final TextField               toleranceField    = new TextField("2");
    private final TextField               includeMasksField = new TextField();
    private final TextField               excludeMasksField = new TextField();
    private final Button                  applyButton       = new Button("Apply");

    // ── State ─────────────────────────────────────────────────────────────────
    private Consumer<FolderComparisonOptions> onApply;
    private FolderComparisonOptions           baseOptions = FolderComparisonOptions.defaults();

    // ── Profile export / import (REQ-014.5) ──────────────────────────────────
    private final Button exportProfileButton = new Button("Export Profile");
    private final Button importProfileButton = new Button("Import Profile");

    private Consumer<FilterProfile>          onExportProfile;
    private Supplier<Optional<FilterProfile>> importProfileSupplier;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FolderOptionsBar() {
        matchModeCombo.setId("matchModeCombo");
        matchModeCombo.setAccessibleText("File match mode");
        matchModeCombo.getItems().addAll(FileMatchMode.values());
        matchModeCombo.setValue(FileMatchMode.SIZE_AND_TIMESTAMP);

        toleranceLabel.setStyle("-fx-padding: 0 2 0 8;");
        toleranceField.setId("timestampToleranceField");
        toleranceField.setAccessibleText("Timestamp tolerance in seconds");
        toleranceField.setPrefWidth(48);
        toleranceField.setMaxWidth(60);

        includeMasksField.setId("includeMasksField");
        includeMasksField.setPromptText("Include (e.g. *.java)");
        includeMasksField.setAccessibleText("Include file patterns, comma-separated");
        includeMasksField.setPrefWidth(140);

        excludeMasksField.setId("excludeMasksField");
        excludeMasksField.setPromptText("Exclude (e.g. *.class)");
        excludeMasksField.setAccessibleText("Exclude file patterns, comma-separated");
        excludeMasksField.setPrefWidth(140);

        applyButton.setId("applyOptionsButton");
        applyButton.setAccessibleText("Apply comparison options");

        // Show / hide tolerance fields based on selected match mode (REQ-010)
        matchModeCombo.valueProperty().addListener((obs, o, mode) -> {
            boolean showTol = mode == FileMatchMode.SIZE_AND_TIMESTAMP;
            toleranceLabel.setVisible(showTol);
            toleranceLabel.setManaged(showTol);
            toleranceField.setVisible(showTol);
            toleranceField.setManaged(showTol);
        });

        applyButton.setOnAction(e -> {
            if (onApply != null) onApply.accept(buildOptions());
        });

        // ── Export / import profile buttons (REQ-014.5) ──────────────────────
        exportProfileButton.setId("exportProfileButton");
        exportProfileButton.setAccessibleText("Export filter profile");
        importProfileButton.setId("importProfileButton");
        importProfileButton.setAccessibleText("Import filter profile");

        exportProfileButton.setOnAction(e -> {
            if (onExportProfile != null) onExportProfile.accept(buildFilterProfile());
        });

        importProfileButton.setOnAction(e -> {
            if (importProfileSupplier != null) {
                Optional<FilterProfile> imported = importProfileSupplier.get();
                imported.ifPresent(p -> {
                    includeMasksField.setText(String.join(", ", p.includeMasks()));
                    excludeMasksField.setText(String.join(", ", p.excludeMasks()));
                });
            }
        });

        setSpacing(6);
        setPadding(new Insets(3, 8, 3, 8));
        setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(
                new Label("Match:"), matchModeCombo,
                toleranceLabel, toleranceField,
                new Label("Include:"), includeMasksField,
                new Label("Exclude:"), excludeMasksField,
                applyButton,
                new Separator(Orientation.VERTICAL),
                exportProfileButton, importProfileButton);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Populates all controls from an existing options record.
     *
     * @param opts the options to display; must not be {@code null}
     */
    public void setBaseOptions(FolderComparisonOptions opts) {
        this.baseOptions = opts;
        matchModeCombo.setValue(opts.matchMode());
        long secs = opts.timestampTolerance().toSeconds();
        toleranceField.setText(String.valueOf(secs > 0 ? secs : 2));
        includeMasksField.setText(String.join(", ", opts.includeMasks()));
        excludeMasksField.setText(String.join(", ", opts.excludeMasks()));
    }

    /**
     * Registers the callback that fires when the user clicks "Apply".
     *
     * @param callback receives the {@link FolderComparisonOptions} built from the current fields
     */
    public void setOnApply(Consumer<FolderComparisonOptions> callback) {
        this.onApply = callback;
    }

    /**
     * Builds a {@link FolderComparisonOptions} from the current control values,
     * preserving {@code manualIgnores} and content options from the {@link #baseOptions}.
     *
     * @return assembled options; never {@code null}
     */
    public FolderComparisonOptions buildOptions() {
        FileMatchMode mode = matchModeCombo.getValue();
        long secs;
        try {
            secs = Long.parseLong(toleranceField.getText().trim());
            if (secs < 0) secs = 0;
        } catch (NumberFormatException ex) {
            secs = 2;
        }
        List<String> includes = parseMasks(includeMasksField.getText());
        List<String> excludes = parseMasks(excludeMasksField.getText());
        return baseOptions
                .withMatchMode(mode)
                .withTimestampTolerance(Duration.ofSeconds(secs))
                .withIncludeMasks(includes)
                .withExcludeMasks(excludes);
    }

    /**
     * Registers the callback invoked when the user clicks "Export Profile".
     * The callback receives a {@link FilterProfile} built from the current masks.
     *
     * @param callback receives the profile; may be {@code null} to deregister
     */
    public void setOnExportProfile(Consumer<FilterProfile> callback) {
        this.onExportProfile = callback;
    }

    /**
     * Provides a supplier that is called when the user clicks "Import Profile".
     * The supplier should show a file-chooser (or in tests return a pre-built profile)
     * and return the imported profile, or {@link Optional#empty()} to cancel.
     *
     * @param supplier supplier of an optional imported profile; may be {@code null}
     */
    public void setImportProfileSupplier(Supplier<Optional<FilterProfile>> supplier) {
        this.importProfileSupplier = supplier;
    }

    /**
     * Builds a {@link FilterProfile} from the current include/exclude mask fields.
     * The profile name is always {@code "profile"}; callers may rename it.
     *
     * @return current profile; never {@code null}
     */
    public FilterProfile buildFilterProfile() {
        return new FilterProfile("profile",
                parseMasks(includeMasksField.getText()),
                parseMasks(excludeMasksField.getText()));
    }


    ComboBox<FileMatchMode> getMatchModeCombo()     { return matchModeCombo; }
    TextField               getToleranceField()     { return toleranceField; }
    TextField               getIncludeMasksField()  { return includeMasksField; }
    TextField               getExcludeMasksField()  { return excludeMasksField; }
    Button                  getApplyButton()        { return applyButton; }
    Button                  getExportProfileButton(){ return exportProfileButton; }
    Button                  getImportProfileButton(){ return importProfileButton; }
    // ── Static helpers ────────────────────────────────────────────────────────

    private static List<String> parseMasks(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
