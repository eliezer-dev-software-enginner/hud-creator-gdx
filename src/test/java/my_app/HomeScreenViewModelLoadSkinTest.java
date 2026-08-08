package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import megalodonte.components.layout_components.Canva;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;
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

/**
 * "Load Skin" used to always open its file chooser in the bundled example
 * assets directory, ignoring wherever the user had actually been browsing —
 * most apps remember the last folder a file was picked from instead.
 * {@link HomeScreenViewModel#loadSkinFrom} is the testable half of
 * {@code handleLoad()} (after the dialog and the confirmation, if one was
 * shown); {@code initialSkinDirectory()} itself just feeds a
 * {@link javafx.stage.FileChooser}, so it's exercised indirectly here via
 * {@link HomeScreenViewModel#lastSkinDirectory()}. Also covers the newer
 * "loading a skin over an existing project used to leave the old widgets on
 * screen, silently referencing a skin that's no longer loaded" fix -
 * {@code loadSkinFrom} now clears the Canva, but only once the new skin
 * actually loads successfully (a bad file leaves the current project
 * completely untouched instead of destroying it for nothing). The actual
 * confirmation dialog ({@code handleLoad()}'s own gate before even opening
 * the file chooser) isn't covered here - a real native {@code Alert} can't
 * be driven from a headless test, same reasoning as every other dialog in
 * this class.
 */
class HomeScreenViewModelLoadSkinTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void loadingASkinRemembersItsDirectory() {
        HomeScreenViewModel viewModel = new HomeScreenViewModel();

        viewModel.loadSkinFrom(EXAMPLE_SKIN);

        assertEquals(
                EXAMPLE_SKIN.toAbsolutePath().normalize().getParent(),
                viewModel.lastSkinDirectory());
    }

    @Test
    void theRememberedDirectorySurvivesEvenIfTheSkinFailsToLoad(@TempDir Path tempDir) {
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        Path badPath = tempDir.resolve("nope.json");

        viewModel.loadSkinFrom(badPath);

        assertNotNull(viewModel.loadErrorState().get(), "sanity check: the load should have failed");
        assertEquals(tempDir.toAbsolutePath().normalize(), viewModel.lastSkinDirectory(),
                "the directory should still be remembered - the user did pick something there");
    }

    @Test
    void theRememberedDirectoryIsPersistedAcrossRestarts(@TempDir Path tempDir) {
        Path settingsFile = tempDir.resolve("settings.json");
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        viewModel.restoreFromAppStorage(settingsFile);

        viewModel.loadSkinFrom(EXAMPLE_SKIN);

        AppSettings persisted = AppStorage.load(settingsFile);
        assertEquals(
                EXAMPLE_SKIN.toAbsolutePath().normalize().getParent().toString(),
                persisted.lastSkinDirectory());
    }

    @Test
    void restoringFromAppStorageAppliesTheRememberedSkinDirectory(@TempDir Path tempDir) {
        Path skinDir = EXAMPLE_SKIN.toAbsolutePath().normalize().getParent();
        Path settingsFile = tempDir.resolve("settings.json");
        AppStorage.save(new AppSettings(true, null, true, skinDir.toString()), settingsFile);

        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        assertNull(viewModel.lastSkinDirectory(), "sanity check: nothing remembered yet on a fresh viewModel");

        viewModel.restoreFromAppStorage(settingsFile);

        assertEquals(skinDir, viewModel.lastSkinDirectory());
    }

    @Test
    void loadingANewSkinOverAnExistingProjectClearsTheCanva() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            viewModel.loadSkinFrom(EXAMPLE_SKIN);
            controller.place(new WidgetSpec.ButtonSpec("default"), 20, 20);
            assertEquals(1, viewModel.placedWidgets().get().size(), "sanity check: something's actually on the Canva");

            // Loading again (a different skin, or even the same one) is exactly
            // what "Load Skin" a second time does - old widgets would otherwise
            // stick around referencing regions/styles from the old skin.
            viewModel.loadSkinFrom(EXAMPLE_SKIN);

            assertEquals(0, viewModel.placedWidgets().get().size(), "the old widgets should be gone");
        });
    }

    @Test
    void aFailedLoadDoesNotClearTheCanva() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            viewModel.loadSkinFrom(EXAMPLE_SKIN);
            controller.place(new WidgetSpec.ButtonSpec("default"), 20, 20);

            viewModel.loadSkinFrom(Path.of("definitely", "does", "not", "exist.json"));

            assertNotNull(viewModel.loadErrorState().get(), "sanity check: the load should have failed");
            assertEquals(1, viewModel.placedWidgets().get().size(),
                    "a failed load shouldn't destroy the current project - only a load that actually succeeds should clear the Canva");
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
