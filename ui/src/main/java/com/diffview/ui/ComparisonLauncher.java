package com.diffview.ui;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Landing screen shown in a freshly opened tab — lets the user pick whether the new
 * comparison is between two files or two folders.
 *
 * <p>Presents two clickable cards side by side. Selecting one fires the corresponding
 * callback ({@link #setOnCompareFiles(Runnable)} / {@link #setOnCompareFolders(Runnable)})
 * so the caller can swap this view out for the actual {@link SelectionBar}-driven
 * comparison content.
 */
public class ComparisonLauncher extends VBox {

    private Runnable onCompareFiles;
    private Runnable onCompareFolders;

    public ComparisonLauncher() {
        setAlignment(Pos.CENTER);
        setSpacing(28);
        setPadding(new Insets(40));
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        Label title = new Label("New Comparison");
        title.getStyleClass().add(Styles.TITLE_1);

        Label subtitle = new Label("Choose what you'd like to compare");
        subtitle.getStyleClass().addAll(Styles.TEXT_MUTED);

        VBox heading = new VBox(6, title, subtitle);
        heading.setAlignment(Pos.CENTER);

        var filesCard = card("🗎", "Compare Files",
                "Line-by-line diff with merge and editing.",
                () -> { if (onCompareFiles != null) onCompareFiles.run(); });
        filesCard.setId("compareFilesCard");

        var foldersCard = card("🗁", "Compare Folders",
                "Recursively diff folder trees and sync files.",
                () -> { if (onCompareFolders != null) onCompareFolders.run(); });
        foldersCard.setId("compareFoldersCard");

        HBox cards = new HBox(20, filesCard, foldersCard);
        cards.setAlignment(Pos.CENTER);

        getChildren().addAll(heading, cards);
        VBox.setVgrow(cards, Priority.NEVER);
    }

    /** Registers the callback invoked when the user picks the "Compare Files" card. */
    public void setOnCompareFiles(Runnable handler) {
        this.onCompareFiles = handler;
    }

    /** Registers the callback invoked when the user picks the "Compare Folders" card. */
    public void setOnCompareFolders(Runnable handler) {
        this.onCompareFolders = handler;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static VBox card(String glyph, String heading, String description, Runnable action) {
        Label icon = new Label(glyph);
        icon.setStyle("-fx-font-size: 36px;");

        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add(Styles.TITLE_3);

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_CAPTION);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        descriptionLabel.setPrefWidth(188);
        descriptionLabel.setMaxWidth(188);
        descriptionLabel.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        VBox card = new VBox(10, icon, headingLabel, descriptionLabel);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28, 24, 28, 24));
        card.setPrefWidth(240);
        card.setMinWidth(240);
        card.setMaxWidth(240);
        card.getStyleClass().addAll(Styles.ELEVATED_1, Styles.INTERACTIVE, Styles.BG_DEFAULT, "comparison-card");
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> action.run());

        return card;
    }
}
