package my_app.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStorageTest {

    @Test
    void loadReturnsDefaultsWhenFileIsMissing(@TempDir Path tempDir) {
        AppSettings settings = AppStorage.load(tempDir.resolve("does-not-exist.json"));

        assertFalse(settings.showingGrid());
        assertNull(settings.lastLayoutFile());
    }

    @Test
    void loadReturnsDefaultsInsteadOfThrowingWhenFileIsCorrupt(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("settings.json");
        Files.writeString(file, "{ not valid json");

        AppSettings settings = AppStorage.load(file);

        assertFalse(settings.showingGrid());
        assertNull(settings.lastLayoutFile());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path tempDir) {
        Path file = tempDir.resolve("nested/settings.json");
        AppSettings original = new AppSettings(true, "/some/path/hud.json");

        AppStorage.save(original, file);

        assertTrue(Files.isRegularFile(file), "save() should create missing parent directories");
        assertEquals(original, AppStorage.load(file));
    }
}
