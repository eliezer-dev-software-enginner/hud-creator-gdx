package my_app;

import javafx.stage.FileChooser;
import megalodonte.application.MegalodonteApp;
import megalodonte.base.state.State;
import megalodonte.base.theme.ThemeManager;
import megalodonte.v2.ListState;
import my_app.project.SkinAssetExporter;
import my_app.project.UiLayout;
import my_app.project.UiLayoutAssembler;
import my_app.project.UiLayoutReader;
import my_app.project.UiLayoutWriter;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;
import my_app.widget.PlacedWidget;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HomeScreenViewModel {

    // Default matches libgdx-example-game's own viewport (GameScreen.VIEWPORT_WIDTH/HEIGHT).
    private final State<SkinModel> skin = State.of(null);
    private final State<String> loadError = State.of(null);
    private final State<Integer> canvasWidth = State.of(640);
    private final State<Integer> canvasHeight = State.of(360);
    private final ListState<PlacedWidget> placedWidgets = ListState.ofEmpty();
    private final AtomicInteger nextWidgetId = new AtomicInteger(1);
    private final State<String> statusMessage = State.of("");
    private final State<String> selectedWidgetId = State.of(null);
    private final State<Boolean> showingGrid = State.of(true);
    private final State<String> backgroundImagePath = State.of(null);
    private CanvasController canvasController; // set once by HomeScreen, after both it and the Canva exist
    private Path currentProjectFile; // last file Save wrote to, or the one Load Layout just read - handleSave() overwrites it directly instead of prompting again
    private Path appStorageFile = AppStorage.DEFAULT_FILE;
    private boolean appStorageEnabled = false; // only true after restoreFromAppStorage() runs - keeps plain `new HomeScreenViewModel()` in tests from writing to disk

    public State<SkinModel> skinState() {
        return skin;
    }

    public State<String> loadErrorState() {
        return loadError;
    }

    public State<Integer> canvasWidthState() {
        return canvasWidth;
    }

    public State<Integer> canvasHeightState() {
        return canvasHeight;
    }

    /**
     * Widgets dropped onto the Canva so far — bookkeeping for now, will back
     * JSON export once that's built (Fase 5). {@link CanvasController} is the
     * only writer.
     */
    public ListState<PlacedWidget> placedWidgets() {
        return placedWidgets;
    }

    public String nextWidgetId() {
        return "widget-" + nextWidgetId.getAndIncrement();
    }

    void attachCanvasController(CanvasController controller) {
        this.canvasController = controller;
    }

    /** The controller for whichever Canva is currently on screen — a new one every {@code HomeScreen.render()} call (tests only; production code goes through {@link #canvasController}'s call sites above). */
    CanvasController canvasController() {
        return canvasController;
    }

    /**
     * Restores the grid toggle and auto-reopens the last layout JSON from
     * {@link AppStorage} — call once, from {@code HomeScreen.onMount()}. Does
     * nothing (not even an error) if there's no settings file yet (first
     * run) or the remembered layout file no longer exists.
     */
    public void restoreFromAppStorage() {
        restoreFromAppStorage(AppStorage.DEFAULT_FILE);
    }

    /** The {@link Path}-taking half of {@link #restoreFromAppStorage()} — separated out so tests can point at a temp file instead of the real one. */
    void restoreFromAppStorage(Path file) {
        appStorageFile = file;
        appStorageEnabled = true;

        AppSettings settings = AppStorage.load(file);
        showingGrid.set(settings.showingGrid());

        if (settings.lastLayoutFile() != null) {
            Path layoutFile = Path.of(settings.lastLayoutFile());
            if (Files.isRegularFile(layoutFile)) {
                loadLayoutFrom(layoutFile);
            }
        }

        // Only wired up now (not unconditionally at construction) - subscribing earlier would
        // fire immediately with the just-applied defaults/loaded value and write to disk even
        // for viewModels tests construct directly without ever calling this method.
        showingGrid.subscribe(value -> persistAppStorage());
        // The active theme isn't a field on this viewModel (ThemeManager already owns it
        // globally, see Main) - just re-persist whenever it changes, same as the grid toggle.
        ThemeManager.state().subscribe(theme -> persistAppStorage());
    }

    private void persistAppStorage() {
        if (!appStorageEnabled) return;
        String lastLayoutFile = currentProjectFile == null ? null : currentProjectFile.toString();
        boolean isLightTheme = ThemeManager.theme() == Themes.light;
        AppStorage.save(new AppSettings(showingGrid.get(), lastLayoutFile, isLightTheme), appStorageFile);
    }

    /** Feedback from the last Save/Export attempt (success or failure), shown in the status bar. */
    public State<String> statusMessageState() {
        return statusMessage;
    }

    /** Id of the widget currently selected on the Canva (click to select, click empty canvas to deselect), or null. Drives the "Properties" panel. */
    public State<String> selectedWidgetIdState() {
        return selectedWidgetId;
    }

    /** Whether the alignment grid overlay is shown on the Canva. Toggled by the "Grid" checkbox in {@link CanvasSizeToolbar}. */
    public State<Boolean> showingGridState() {
        return showingGrid;
    }

    /**
     * Path to a reference image shown behind the Canva's contents — purely
     * visual. Saved in the JSON (as {@code backgroundImagePath}, relative
     * when possible) so "Load Layout" restores it, but real games draw
     * backgrounds with {@code SpriteBatch}, not the Scene2D UI this builder
     * produces, so a libGDX-side parser is expected to ignore this field.
     */
    public State<String> backgroundImagePathState() {
        return backgroundImagePath;
    }

    public void handleLoadBackgroundImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose background image (visual only — ignored by the game's parser)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));

        File selected = chooser.showOpenDialog(MegalodonteApp.getCurrentContext().javafxStage());
        if (selected == null) return;

        backgroundImagePath.set(selected.toPath().toAbsolutePath().normalize().toString());
        statusMessage.set("Background image loaded (visual only — ignored by the game's parser)");
    }

    public void handleClearBackgroundImage() {
        backgroundImagePath.set(null);
    }

    /**
     * Starts a fresh project: clears the canvas ({@link CanvasController#clear()}),
     * forgets the loaded skin/background image/load error, resets the canvas
     * size back to the default, and forgets {@link #currentProjectFile} — the
     * next "Save" opens the dialog again instead of silently overwriting
     * whatever was open before.
     */
    public void handleNew() {
        if (canvasController != null) {
            canvasController.clear();
        }
        skin.set(null);
        loadError.set(null);
        backgroundImagePath.set(null);
        canvasWidth.set(640);
        canvasHeight.set(360);
        nextWidgetId.set(1);
        currentProjectFile = null;
        statusMessage.set("");
        persistAppStorage();
    }

    public void handleLoad() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load skin (skin.json)");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("libGDX skin (skin.json)", "*.json"));

        Path exampleAssetsDir = Path.of("source.images.and.assets", "extract to your assets folder");
        if (Files.isDirectory(exampleAssetsDir)) {
            chooser.setInitialDirectory(exampleAssetsDir.toAbsolutePath().toFile());
        }

        File selected = chooser.showOpenDialog(MegalodonteApp.getCurrentContext().javafxStage());
        if (selected == null) return;

        try {
            SkinModel loaded = SkinLoader.load(selected.toPath());
            loadError.set(null);
            skin.set(loaded);
        } catch (RuntimeException e) {
            loadError.set(e.getMessage());
        }
    }

    /**
     * Saves the layout as-is, referencing the skin wherever it currently sits
     * on disk — no copying. Overwrites {@link #currentProjectFile} directly
     * (no dialog) once one is known — set after any successful save, or after
     * {@link #loadLayoutFrom}, so "Save" on a loaded layout overwrites it
     * instead of asking where to save every time.
     */
    public void handleSave() {
        SkinModel currentSkin = skin.get();
        if (currentSkin == null) {
            statusMessage.set("Load a skin before saving.");
            return;
        }

        if (currentProjectFile != null) {
            saveTo(currentProjectFile);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save project");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("scene2d-builder project", "*.json"));
        chooser.setInitialFileName("project.json");

        Path projectsDir = Path.of("projects");
        if (Files.isDirectory(projectsDir)) {
            chooser.setInitialDirectory(projectsDir.toAbsolutePath().toFile());
        }

        File selected = chooser.showSaveDialog(MegalodonteApp.getCurrentContext().javafxStage());
        if (selected == null) return;

        saveTo(selected.toPath());
    }

    /** The part of {@link #handleSave()} after the file dialog — separated out so it's callable without a real save dialog (tests). */
    void saveTo(Path outputFile) {
        SkinModel currentSkin = skin.get();
        try {
            String skinPath = UiLayoutAssembler.relativeSkinPath(currentSkin.skinJsonPath(), outputFile);
            String backgroundPath = relativizedBackgroundImagePath(outputFile);
            UiLayout layout = UiLayoutAssembler.assemble(
                    skinPath, canvasWidth.get(), canvasHeight.get(), backgroundPath, placedWidgets.get());
            UiLayoutWriter.write(layout, outputFile);
            currentProjectFile = outputFile;
            persistAppStorage();
            statusMessage.set("Project saved to " + outputFile);
        } catch (IOException | RuntimeException e) {
            statusMessage.set("Failed to save: " + e.getMessage());
        }
    }

    private String relativizedBackgroundImagePath(Path outputFile) {
        String rawPath = backgroundImagePath.get();
        return rawPath == null ? null : UiLayoutAssembler.relativePath(Path.of(rawPath), outputFile);
    }

    /**
     * Exports the layout into a libGDX project's assets: copies the skin's
     * files alongside the JSON (in a {@code skin/} subfolder) so the export
     * is self-contained, unlike {@link #handleSave()}.
     */
    public void handleExport() {
        SkinModel currentSkin = skin.get();
        if (currentSkin == null) {
            statusMessage.set("Load a skin before exporting.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export UI to the libGDX project");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Layout JSON", "*.json"));
        chooser.setInitialFileName("hud.json");

        Path defaultExportDir = Path.of("..", "libgdx-example-game", "assets", "ui");
        if (Files.isDirectory(defaultExportDir)) {
            chooser.setInitialDirectory(defaultExportDir.toAbsolutePath().normalize().toFile());
        }

        File selected = chooser.showSaveDialog(MegalodonteApp.getCurrentContext().javafxStage());
        if (selected == null) return;

        exportTo(selected.toPath());
    }

    /** The part of {@link #handleExport()} after the file dialog — separated out so it's callable without a real save dialog (tests). */
    void exportTo(Path rawOutputFile) {
        SkinModel currentSkin = skin.get();
        try {
            Path outputFile = rawOutputFile.toAbsolutePath().normalize();
            Path skinTargetDir = outputFile.getParent().resolve("skin");
            String skinFileName = SkinAssetExporter.copyInto(currentSkin, skinTargetDir);
            String backgroundPath = relativizedBackgroundImagePath(outputFile);

            UiLayout layout = UiLayoutAssembler.assemble(
                    "skin/" + skinFileName, canvasWidth.get(), canvasHeight.get(), backgroundPath, placedWidgets.get());
            UiLayoutWriter.write(layout, outputFile);
            statusMessage.set("UI exported to " + outputFile);
        } catch (IOException | RuntimeException e) {
            statusMessage.set("Failed to export: " + e.getMessage());
        }
    }

    /** Reloads a layout JSON (written by Save/Export) back onto the Canva — skin, canvas size, and every widget at its saved position. */
    public void handleLoadLayout() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load layout (JSON)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Layout JSON", "*.json"));

        Path defaultExportDir = Path.of("..", "libgdx-example-game", "assets", "ui");
        if (Files.isDirectory(defaultExportDir)) {
            chooser.setInitialDirectory(defaultExportDir.toAbsolutePath().normalize().toFile());
        }

        File selected = chooser.showOpenDialog(MegalodonteApp.getCurrentContext().javafxStage());
        if (selected == null) return;

        loadLayoutFrom(selected.toPath());
    }

    /** The part of {@link #handleLoadLayout()} after the file dialog — separated out so it's callable without a real open dialog (tests). */
    void loadLayoutFrom(Path layoutFile) {
        try {
            UiLayout layout = UiLayoutReader.read(layoutFile);
            Path skinFile = layoutFile.toAbsolutePath().normalize().getParent().resolve(layout.skinPath());
            SkinModel loadedSkin = SkinLoader.load(skinFile);

            List<PlacedWidget> widgets = layout.widgets().stream().map(UiLayoutAssembler::fromDto).toList();

            loadError.set(null);
            skin.set(loadedSkin);
            canvasWidth.set(layout.canvasWidth());
            canvasHeight.set(layout.canvasHeight());
            backgroundImagePath.set(resolveBackgroundImagePath(layout, layoutFile));
            bumpNextWidgetIdPast(widgets);
            canvasController.loadLayout(widgets);
            currentProjectFile = layoutFile;
            persistAppStorage();

            statusMessage.set("Layout loaded from " + layoutFile);
        } catch (IOException | RuntimeException e) {
            statusMessage.set("Failed to load layout: " + e.getMessage());
        }
    }

    private static String resolveBackgroundImagePath(UiLayout layout, Path layoutFile) {
        if (layout.backgroundImagePath() == null) return null;
        return layoutFile.toAbsolutePath().normalize().getParent()
                .resolve(layout.backgroundImagePath())
                .normalize()
                .toString();
    }

    /** So new widgets dragged in after a load don't collide with ids the loaded layout already used. */
    private void bumpNextWidgetIdPast(List<PlacedWidget> widgets) {
        int max = 0;
        for (PlacedWidget widget : widgets) {
            String id = widget.id();
            if (id != null && id.startsWith("widget-")) {
                try {
                    max = Math.max(max, Integer.parseInt(id.substring("widget-".length())));
                } catch (NumberFormatException ignored) {
                    // non-numeric suffix - not one of ours, ignore
                }
            }
        }
        int next = max + 1;
        nextWidgetId.updateAndGet(current -> Math.max(current, next));
    }
}
