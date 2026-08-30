package com.diffview.app;

import com.diffview.core.diff.LineDiffEngine;
import com.diffview.core.folder.DefaultFolderDiffEngine;
import com.diffview.core.service.ComparisonService;
import com.diffview.core.service.DefaultComparisonService;
import com.diffview.infra.concurrent.TaskExecutor;
import com.diffview.infra.concurrent.PooledTaskExecutor;
import com.diffview.infra.encoding.JUniversalChardetDetector;
import com.diffview.infra.hash.Sha256HashService;
import com.diffview.infra.io.FileIOService;
import com.diffview.infra.io.NioFileIOService;
import com.diffview.model.ComparisonOptions;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.ThemeMode;
import com.diffview.ui.AboutDialog;
import com.diffview.ui.ComparisonLauncher;
import com.diffview.ui.FileComparisonView;
import com.diffview.ui.FolderComparisonView;
import com.diffview.ui.SelectionBar;
import com.diffview.ui.ThemeManager;
import com.diffview.viewmodel.FileComparisonViewModel;
import com.diffview.viewmodel.FileDiffRequest;
import com.diffview.viewmodel.FolderComparisonViewModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Application entry point — tab-based shell.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │  File   Help                                    (menu bar) │
 * ├──────────────────────────────────────────────────────────┤
 * │  TabPane                                                 │
 * │  ┌─────────────┐ ┌─────────────┐                        │
 * │  │ New Comp  ✕ │ │ [File] a↔b✕ │  …                     │
 * │  └─────────────┘ └─────────────┘                        │
 * │  ┌────────────────────────────────────────────────────┐  │
 * │  │  ComparisonLauncher (cards) — until a mode is picked │  │
 * │  │  … replaced by SelectionBar + comparison content …  │  │
 * │  └────────────────────────────────────────────────────┘  │
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * Each tab opens on a {@link ComparisonLauncher} (pick File or Folder comparison);
 * picking a card swaps the tab's content for the real comparison view. Each tab owns
 * its own ViewModels and Views so comparisons are fully independent. Double-clicking a
 * file inside a Folder Comparison opens a new File Comparison tab directly.
 */
public class MainApp extends Application {

    // ── Shared services (stateless — safe to share across tabs) ───────────────
    private FileIOService     fileIO;
    private LineDiffEngine    diffEngine;
    private ComparisonService service;
    private TaskExecutor      executor;

    private Stage stage;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        ThemeManager.applyTheme(ThemeMode.LIGHT);

        fileIO     = new NioFileIOService(new JUniversalChardetDetector());
        executor   = new PooledTaskExecutor();
        diffEngine = new LineDiffEngine();
        service    = new DefaultComparisonService(
                diffEngine,
                new DefaultFolderDiffEngine(),
                fileIO,
                new Sha256HashService(),
                executor);

        // ── Tab pane ───────────────────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getStyleClass().add("floating"); // AtlantaFX Styles.TABS_FLOATING

        // ── Menu bar ───────────────────────────────────────────────────────────
        MenuBar menuBar = buildMenuBar(tabPane);

        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(tabPane);

        // Open one default launcher tab on startup
        addTab(tabPane);

        Scene scene = new Scene(root, 1200, 800);
        // css lives in the ui module classpath
        addStylesheet(scene, "/css/diff-colors.css");
        addStylesheet(scene, "/css/app.css");

        primaryStage.setTitle("DiffView");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private static void addStylesheet(Scene scene, String path) {
        URL cssUrl = MainApp.class.getResource(path);
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
    }

    // ── Menu bar ───────────────────────────────────────────────────────────────

    private MenuBar buildMenuBar(TabPane tabPane) {
        MenuItem newComparison = new MenuItem("New Comparison");
        newComparison.setAccelerator(KeyCombination.keyCombination("Shortcut+N"));
        newComparison.setOnAction(e -> addTab(tabPane));

        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> Platform.exit());

        Menu fileMenu = new Menu("File", null,
                newComparison, new SeparatorMenuItem(), exit);

        MenuItem about = new MenuItem("About DiffView");
        about.setOnAction(e -> AboutDialog.show(stage, appVersion()));

        Menu helpMenu = new Menu("Help", null, about);

        return new MenuBar(fileMenu, helpMenu);
    }

    private static String appVersion() {
        String v = MainApp.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    // ── Tab factory ────────────────────────────────────────────────────────────

    /**
     * Opens a new tab showing the {@link ComparisonLauncher} cards, adds it to
     * {@code tabPane}, and selects it. Picking a card swaps the tab's content for
     * the real File or Folder comparison view.
     */
    private void addTab(TabPane tabPane) {
        Tab tab = new Tab("New Comparison");

        ComparisonLauncher launcher = new ComparisonLauncher();
        launcher.setOnCompareFiles(()   -> tab.setContent(buildComparisonContent(tab, tabPane, false)));
        launcher.setOnCompareFolders(() -> tab.setContent(buildComparisonContent(tab, tabPane, true)));
        tab.setContent(launcher);

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    /**
     * Builds the SelectionBar-driven comparison content for {@code tab} once the
     * user has picked a mode from the {@link ComparisonLauncher}.
     * Each tab has its own ViewModels and Views — comparisons are independent.
     *
     * @param folder {@code true} for a Folder Comparison, {@code false} for File
     */
    private BorderPane buildComparisonContent(Tab tab, TabPane tabPane, boolean folder) {
        tab.setText(folder ? "Folder Comparison" : "File Comparison");

        // Per-tab ViewModels
        FileComparisonViewModel   fileVm   = new FileComparisonViewModel(service, diffEngine, executor, fileIO);
        FolderComparisonViewModel folderVm = new FolderComparisonViewModel(service, executor);

        // Per-tab Views
        FileComparisonView   fileView   = new FileComparisonView();
        FolderComparisonView folderView = new FolderComparisonView();
        fileView.bindViewModel(fileVm);
        folderView.bindViewModel(folderVm);

        // Content area (center of the tab) — filled when Compare is pressed
        BorderPane contentArea = new BorderPane();
        SelectionBar selBar = new SelectionBar();
        if (folder) selBar.setFolderMode(true);

        // Folder drill-down → open a new File Comparison tab
        folderView.setOnFileDiffRequested(req -> {
            if (req instanceof FileDiffRequest.Actual actual) {
                openFileDiffTab(tabPane, actual);
            } else if (req instanceof FileDiffRequest.Placeholder ph) {
                String msg = ph.leftSide()
                        ? "<- Only on left: " + ph.side().getFileName()
                        : "Only on right: " + ph.side().getFileName() + " ->";
                Label lbl = new Label(msg);
                lbl.setStyle("-fx-font-size: 14px; -fx-text-fill: #777;");
                Tab t = new Tab(ph.side().getFileName().toString(), lbl);
                tabPane.getTabs().add(t);
                tabPane.getSelectionModel().select(t);
            }
        });

        selBar.setOnCompare(request -> {
            String ln = request.left() .getFileName().toString();
            String rn = request.right().getFileName().toString();
            if (request.folder()) {
                tab.setText("[Folder] " + ln + " \u2194 " + rn);
                contentArea.setCenter(folderView);
                folderView.startCompare(request.left(), request.right(), FolderComparisonOptions.defaults());
            } else {
                tab.setText("[File] " + ln + " \u2194 " + rn);
                contentArea.setCenter(fileView);
                fileVm.compare(request.left(), request.right(), ComparisonOptions.defaults());
            }
        });

        BorderPane tabRoot = new BorderPane();
        tabRoot.setTop(selBar);
        tabRoot.setCenter(contentArea);
        return tabRoot;
    }

    /**
     * Opens a new File Comparison tab for a file pair drilled into from a Folder Comparison.
     * The tab is selected immediately and the comparison starts right away.
     */
    private void openFileDiffTab(TabPane tabPane, FileDiffRequest.Actual req) {
        FileComparisonViewModel vm   = new FileComparisonViewModel(service, diffEngine, executor, fileIO);
        FileComparisonView      view = new FileComparisonView();
        view.bindViewModel(vm);

        String title = "[File] " + req.left().getFileName() + " \u2194 " + req.right().getFileName();
        Tab tab = new Tab(title, view);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        vm.compare(req.left(), req.right(), req.options());
    }

    public static void main(String[] args) {
        // --smoke-test: headless CI validation of the packaged runtime (REQ-17.2).
        for (String arg : args) {
            if ("--smoke-test".equals(arg)) {
                PackagingSmokeTest.run(); // calls System.exit(); never returns
                return;
            }
        }
        launch(args);
    }
}
