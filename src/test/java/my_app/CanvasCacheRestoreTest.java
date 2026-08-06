package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quitting the app used to lose everything on the Canva unless the user had
 * explicitly hit "Save"/"Export" first. {@link HomeScreenViewModel} now
 * mirrors the Canva's current state (skin, size, background, every placed
 * widget) into an auto-managed {@code my_app.storage.CanvasCache} on every
 * change, and restores it on the next {@link HomeScreenViewModel#restoreFromAppStorage}
 * even when nothing was ever explicitly saved.
 */
class CanvasCacheRestoreTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void unsavedWorkSurvivesIntoAFreshViewModelViaTheCache(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            Path settingsFile = tempDir.resolve("settings.json");

            HomeScreenViewModel firstSession = new HomeScreenViewModel();
            CanvasController firstController = new CanvasController(new Canva(), firstSession);
            firstSession.attachCanvasController(firstController);
            firstSession.restoreFromAppStorage(settingsFile);

            firstSession.canvasWidthState().set(500);
            firstSession.canvasHeightState().set(280);
            firstSession.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            firstController.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            // Never called handleSave()/handleExport() - simulates just closing the app.

            HomeScreenViewModel secondSession = new HomeScreenViewModel();
            secondSession.attachCanvasController(new CanvasController(new Canva(), secondSession));
            secondSession.restoreFromAppStorage(settingsFile);

            assertEquals(500, secondSession.canvasWidthState().get());
            assertEquals(280, secondSession.canvasHeightState().get());
            assertNotNull(secondSession.skinState().get());
            assertEquals(1, secondSession.placedWidgets().get().size());
        });
    }

    @Test
    void handleNewClearsTheCacheSoTheNextLaunchStartsBlank(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            Path settingsFile = tempDir.resolve("settings.json");

            HomeScreenViewModel firstSession = new HomeScreenViewModel();
            CanvasController firstController = new CanvasController(new Canva(), firstSession);
            firstSession.attachCanvasController(firstController);
            firstSession.restoreFromAppStorage(settingsFile);
            firstSession.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            firstController.place(new WidgetSpec.ButtonSpec("default"), 10, 10);

            firstSession.handleNew();

            HomeScreenViewModel secondSession = new HomeScreenViewModel();
            secondSession.attachCanvasController(new CanvasController(new Canva(), secondSession));
            secondSession.restoreFromAppStorage(settingsFile);

            assertNull(secondSession.skinState().get());
            assertEquals(0, secondSession.placedWidgets().get().size());
        });
    }

    @Test
    void theCacheTakesPriorityOverAStaleExplicitSaveButSaveStillTargetsIt(@TempDir Path tempDir) throws Exception {
        runOnFxThreadAndWait(() -> {
            Path settingsFile = tempDir.resolve("settings.json");
            Path projectFile = tempDir.resolve("project.json");

            HomeScreenViewModel firstSession = new HomeScreenViewModel();
            CanvasController firstController = new CanvasController(new Canva(), firstSession);
            firstSession.attachCanvasController(firstController);
            firstSession.restoreFromAppStorage(settingsFile);
            firstSession.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            firstController.place(new WidgetSpec.ButtonSpec("default"), 10, 10);
            firstSession.saveTo(projectFile); // explicit save - 1 widget on disk

            firstController.place(new WidgetSpec.ButtonSpec("toggle"), 80, 80); // unsaved edit - 2 widgets now, only in the cache

            HomeScreenViewModel secondSession = new HomeScreenViewModel();
            secondSession.attachCanvasController(new CanvasController(new Canva(), secondSession));
            secondSession.restoreFromAppStorage(settingsFile);

            assertEquals(2, secondSession.placedWidgets().get().size(),
                    "the cache (2 widgets) should win over the stale explicit save (1 widget) on disk");

            // Save() should still overwrite the file the user actually chose, not the cache.
            secondSession.handleSave();
            assertTrue(secondSession.statusMessageState().get().startsWith("Project saved to " + projectFile),
                    "unexpected status: " + secondSession.statusMessageState().get());
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
