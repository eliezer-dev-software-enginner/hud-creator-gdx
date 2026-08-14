package my_app.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the app's own preferences — as opposed to a UI layout, see
 * {@link my_app.project.UiLayout} for that — across runs: the grid toggle
 * and which layout JSON to auto-reopen on the next launch. Lives in a small
 * JSON file under the user's home directory by default, independent of
 * whatever directory the app happens to be launched from.
 * <p>
 * The {@code Path}-taking overloads exist so tests can point at a temp file
 * instead of writing into the real user's home directory.
 */
public final class AppStorage {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    public static final Path DEFAULT_FILE = Path.of(System.getProperty("user.home"), ".scene2d-hud-creator-gdx", "settings.json");

    private AppStorage() {
    }

    public static AppSettings load() {
        return load(DEFAULT_FILE);
    }

    /** Never throws — a missing or corrupt settings file just means "start with defaults," not a crash. */
    public static AppSettings load(Path file) {
        if (!Files.isRegularFile(file)) {
            return AppSettings.defaults();
        }
        try {
            return MAPPER.readValue(file.toFile(), AppSettings.class);
        } catch (IOException e) {
            return AppSettings.defaults();
        }
    }

    public static void save(AppSettings settings) {
        save(settings, DEFAULT_FILE);
    }

    public static void save(AppSettings settings, Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().normalize().getParent());
            MAPPER.writeValue(file.toFile(), settings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
