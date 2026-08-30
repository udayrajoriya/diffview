package com.diffview.ui;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * "About DiffView" dialog — application name, version, and credits.
 */
public final class AboutDialog {

    private AboutDialog() {}

    /**
     * Builds and shows the About dialog, blocking until the user closes it.
     *
     * @param owner   the window to center the dialog on; may be {@code null}
     * @param version the version string to display (e.g. {@code "0.1.0"} or {@code "dev"})
     */
    public static void show(Window owner, String version) {
        Label name = new Label("DiffView");
        name.getStyleClass().add(Styles.TITLE_1);

        Label tagline = new Label("Compare files and folders, side by side.");
        tagline.getStyleClass().add(Styles.TEXT_MUTED);

        Label versionLabel = new Label("Version " + version);
        versionLabel.getStyleClass().add(Styles.TEXT_CAPTION);

        Label credits = new Label("Created by Uday Rajoriya");
        credits.getStyleClass().add(Styles.TEXT_SUBTLE);

        Label stack = new Label("Built with Java, JavaFX & AtlantaFX");
        stack.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        VBox content = new VBox(6, name, tagline, versionLabel, credits, stack);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(16, 30, 8, 30));

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.initOwner(owner);
        alert.setTitle("About DiffView");
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(ButtonType.CLOSE);
        alert.showAndWait();
    }
}
