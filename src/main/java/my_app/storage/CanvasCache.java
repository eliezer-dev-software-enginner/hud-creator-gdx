package my_app.storage;

import my_app.project.UiLayout;
import my_app.project.UiLayoutReader;
import my_app.project.UiLayoutWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-saves whatever is currently on the Canva (skin, size, background,
 * every placed widget) so quitting the app — without ever having explicitly
 * used "Save"/"Export" — doesn't lose it, the way it used to. Same
 * {@link UiLayout} shape "Save"/"Export"/"Load Layout" already use, just
 * written continuously to a fixed, app-managed file instead of wherever the
 * user chooses; see {@link my_app.HomeScreenViewModel} for when it's written/read.
 * <p>
 * Unlike a real project file, paths inside the cached {@link UiLayout} are
 * absolute rather than relative — there's no meaningful "next to" directory
 * for an app-managed cache file the way there is for a file the user picked.
 */
public final class CanvasCache {

    public static final Path DEFAULT_FILE = Path.of(System.getProperty("user.home"), ".scene2d-buider", "canvas-cache.json");

    private CanvasCache() {
    }

    public static void save(UiLayout layout, Path file) {
        try {
            UiLayoutWriter.write(layout, file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Never throws — a missing or corrupt cache just means "nothing to restore," not a crash. */
    public static UiLayout load(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return UiLayoutReader.read(file);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static void clear(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
