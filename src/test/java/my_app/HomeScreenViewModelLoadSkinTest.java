package my_app;

import my_app.storage.AppSettings;
import my_app.storage.AppStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * "Load Skin" used to always open its file chooser in the bundled example
 * assets directory, ignoring wherever the user had actually been browsing —
 * most apps remember the last folder a file was picked from instead.
 * {@link HomeScreenViewModel#loadSkinFrom} is the testable half of
 * {@code handleLoad()} (after the dialog); {@code initialSkinDirectory()}
 * itself just feeds a {@link javafx.stage.FileChooser}, so it's exercised
 * indirectly here via {@link HomeScreenViewModel#lastSkinDirectory()}.
 */
class HomeScreenViewModelLoadSkinTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

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
}
