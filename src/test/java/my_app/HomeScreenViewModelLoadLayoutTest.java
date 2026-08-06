package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import megalodonte.components.layout_components.Canva;
import my_app.project.UiLayoutReader;
import my_app.skin.SkinLoader;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trips a layout through {@link HomeScreenViewModel#exportTo} then
 * {@link HomeScreenViewModel#loadLayoutFrom} — the "Load Layout" menu
 * item added after the user reported no way to reload an exported JSON back
 * onto the Canva. Runs on the real JavaFX Application Thread since
 * {@code loadLayoutFrom} drives a real {@link Canva} via
 * {@link CanvasController}.
 */
class HomeScreenViewModelLoadLayoutTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void loadedLayoutRestoresSkinCanvasSizeAndWidgetsOntoTheCanva(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            // Export a layout with a nicknamed widget.
            HomeScreenViewModel exportingViewModel = new HomeScreenViewModel();
            exportingViewModel.canvasWidthState().set(400);
            exportingViewModel.canvasHeightState().set(240);
            exportingViewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            exportingViewModel.placedWidgets().add(new PlacedWidget(
                    "widget-1", new WidgetSpec.TextButtonSpec("default", "Play"), 10, 20, "play", null, null));
            exportingViewModel.placedWidgets().add(new PlacedWidget(
                    "widget-2", new WidgetSpec.ImageSpec("arrow"), 30, 40, null, 64.0, 24.0));

            Path outputFile = tempDir.resolve("ui/hud.json");
            exportingViewModel.exportTo(outputFile);

            // Load it back into a fresh viewModel/Canva.
            HomeScreenViewModel loadingViewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            Region pane = (Region) canva.getNode();
            pane.setPrefSize(1, 1); // real size doesn't matter for this assertion
            CanvasController controller = new CanvasController(canva, loadingViewModel);
            loadingViewModel.attachCanvasController(controller);

            loadingViewModel.loadLayoutFrom(outputFile);

            assertTrue(loadingViewModel.statusMessageState().get().startsWith("Layout loaded from"),
                    "unexpected status: " + loadingViewModel.statusMessageState().get());
            assertEquals(400, loadingViewModel.canvasWidthState().get());
            assertEquals(240, loadingViewModel.canvasHeightState().get());
            assertTrue(loadingViewModel.skinState().get() != null);

            var widgets = loadingViewModel.placedWidgets().get();
            assertEquals(2, widgets.size());
            assertEquals("play", widgets.get(0).nickname());
            assertEquals(10, widgets.get(0).x());
            assertEquals(20, widgets.get(0).y());
            assertEquals(null, widgets.get(1).nickname());
            assertEquals(64.0, widgets.get(1).width(), "a resized widget's size should round-trip through Load Layout too");
            assertEquals(24.0, widgets.get(1).height());

            // Actually landed on the Canva, not just in the model.
            assertTrue(controller.nodeFor(widgets.get(0).id()) != null);
            Node resizedNode = controller.nodeFor(widgets.get(1).id());
            assertTrue(resizedNode != null);
            assertEquals(64.0, resizedNode.prefWidth(-1), "the resized node itself, not just the model, should reflect the saved size");
            assertEquals(24.0, resizedNode.prefHeight(-1));

            // New widgets dragged in after loading shouldn't collide with the loaded ids.
            assertEquals("widget-3", loadingViewModel.nextWidgetId());
        });
    }

    @Test
    void handleSaveAfterLoadingOverwritesTheLoadedFileWithoutADialog(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel exportingViewModel = new HomeScreenViewModel();
            exportingViewModel.canvasWidthState().set(320);
            exportingViewModel.canvasHeightState().set(180);
            exportingViewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            exportingViewModel.placedWidgets().add(new PlacedWidget(
                    "widget-1", new WidgetSpec.ButtonSpec("default"), 5, 5, null, null, null));

            Path outputFile = tempDir.resolve("project.json");
            exportingViewModel.exportTo(outputFile);

            HomeScreenViewModel loadingViewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            loadingViewModel.attachCanvasController(new CanvasController(canva, loadingViewModel));
            loadingViewModel.loadLayoutFrom(outputFile);

            // Add a second widget, then Save - must overwrite outputFile directly,
            // with no FileChooser/Stage involved (handleSave() would NPE on
            // MegalodonteApp.getCurrentContext() if it fell through to the dialog branch).
            loadingViewModel.placedWidgets().add(new PlacedWidget(
                    "widget-2", new WidgetSpec.ImageSpec("arrow"), 50, 50, null, null, null));
            loadingViewModel.handleSave();

            assertTrue(loadingViewModel.statusMessageState().get().startsWith("Project saved to"),
                    "unexpected status: " + loadingViewModel.statusMessageState().get());

            var reloaded = UiLayoutReader.read(outputFile);
            assertEquals(2, reloaded.widgets().size(), "handleSave() should have overwritten " + outputFile);
        });
    }

    @Test
    void backgroundImagePathRoundTripsThroughSaveAndLoad(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            // Any real file works as a stand-in "background image" for this test - only the path matters.
            Path backgroundImage = EXAMPLE_SKIN.toAbsolutePath().normalize().getParent().resolve("skin.png");

            HomeScreenViewModel exportingViewModel = new HomeScreenViewModel();
            exportingViewModel.canvasWidthState().set(300);
            exportingViewModel.canvasHeightState().set(200);
            exportingViewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            exportingViewModel.backgroundImagePathState().set(backgroundImage.toString());

            Path outputFile = tempDir.resolve("ui/hud.json");
            exportingViewModel.exportTo(outputFile);

            var written = UiLayoutReader.read(outputFile);
            assertTrue(written.backgroundImagePath() != null, "background image path should have been saved in the JSON");

            HomeScreenViewModel loadingViewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            loadingViewModel.attachCanvasController(new CanvasController(canva, loadingViewModel));
            loadingViewModel.loadLayoutFrom(outputFile);

            String restoredPath = loadingViewModel.backgroundImagePathState().get();
            assertEquals(backgroundImage.toAbsolutePath().normalize().toString(),
                    Path.of(restoredPath).toAbsolutePath().normalize().toString());
        });
    }

    @Test
    void restoreFromAppStorageReopensTheLastLayoutAndAppliesTheGridSetting(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            // A layout to be "the last one open" - exported for real, like a previous session would have.
            HomeScreenViewModel exportingViewModel = new HomeScreenViewModel();
            exportingViewModel.canvasWidthState().set(320);
            exportingViewModel.canvasHeightState().set(180);
            exportingViewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            Path layoutFile = tempDir.resolve("ui/hud.json");
            exportingViewModel.exportTo(layoutFile);

            Path settingsFile = tempDir.resolve("settings.json");
            AppStorage.save(new AppSettings(true, layoutFile.toString(), true, null), settingsFile);

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            viewModel.attachCanvasController(new CanvasController(canva, viewModel));

            viewModel.restoreFromAppStorage(settingsFile);

            assertTrue(viewModel.showingGridState().get(), "grid setting should have been restored");
            assertEquals(320, viewModel.canvasWidthState().get(), "should have auto-reopened the last layout");
            assertTrue(viewModel.skinState().get() != null);
        });
    }

    @Test
    void togglingGridAfterRestoreWritesItBackToAppStorage(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            Path settingsFile = tempDir.resolve("settings.json");
            AppStorage.save(new AppSettings(false, null, true, null), settingsFile);

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            viewModel.attachCanvasController(new CanvasController(canva, viewModel));
            viewModel.restoreFromAppStorage(settingsFile);

            viewModel.showingGridState().set(true);

            assertTrue(AppStorage.load(settingsFile).showingGrid(),
                    "toggling the grid after restoreFromAppStorage() should persist immediately");
        });
    }

    @Test
    void savingAfterRestoreUpdatesTheRememberedLayoutFile(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            Path settingsFile = tempDir.resolve("settings.json");
            AppStorage.save(new AppSettings(false, null, true, null), settingsFile);

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            viewModel.attachCanvasController(new CanvasController(canva, viewModel));
            viewModel.restoreFromAppStorage(settingsFile);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Path newProjectFile = tempDir.resolve("project.json");
            viewModel.saveTo(newProjectFile);

            assertEquals(newProjectFile.toString(), AppStorage.load(settingsFile).lastLayoutFile(),
                    "saveTo() after restoreFromAppStorage() should update the remembered layout file too");
        });
    }

    private static void runOnFxThreadAndWait(RunnableThrowing action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await();

        Throwable failure = error.get();
        if (failure instanceof Exception ex) throw ex;
        if (failure != null) throw new RuntimeException(failure);
    }

    @FunctionalInterface
    private interface RunnableThrowing {
        void run() throws Exception;
    }
}
