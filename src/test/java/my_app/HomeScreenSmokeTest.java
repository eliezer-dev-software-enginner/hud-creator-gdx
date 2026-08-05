package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import megalodonte.base.theme.ThemeManager;
import megalodonte.theme.DefaultTheme;
import my_app.skin.SkinLoader;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Manual visual check for Fase 3 (skin loading wired into HomeScreen) — not a
 * JUnit test. Drives {@link HomeScreenViewModel#skinState()} directly instead
 * of going through the native FileChooser in {@code handleLoad()} (that part
 * is a couple lines of plain JavaFX API and isn't practical to snapshot-test),
 * so this exercises exactly the part that's new and risky: the reactive
 * rebuild of {@link SkinPalettePanel} once a skin loads or fails to.
 * <p>
 * Run via the {@code homeScreenSnapshot} Gradle task, then look at
 * {@code build/home-screen-smoke-test.png}: empty state, error state, and
 * loaded-skin state stacked top to bottom.
 */
public class HomeScreenSmokeTest {

    public static void main(String[] args) throws Exception {
        new JFXPanel(); // starts the JavaFX toolkit without needing an Application/Stage

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ThemeManager.setTheme(new DefaultTheme());
                renderAndSave();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void renderAndSave() {
        // Resized *after* rendering, to prove the Canva reacts to the
        // CanvasSizeToolbar's state rather than just reading it once at
        // construction time.
        HomeScreenViewModel resizeVm = new HomeScreenViewModel();
        javafx.scene.Node resizedNode = renderHomeScreen(resizeVm);
        resizeVm.canvasWidthState().set(300);
        resizeVm.canvasHeightState().set(150);

        javafx.scene.layout.VBox states = new javafx.scene.layout.VBox(16,
                renderHomeScreen(new HomeScreenViewModel()),
                renderHomeScreen(errorViewModel()),
                renderHomeScreen(loadedViewModel()),
                resizedNode
        );

        new Scene(states);
        WritableImage combined = states.snapshot(new SnapshotParameters(), null);
        write(combined, "build/home-screen-smoke-test.png");
    }

    private static HomeScreenViewModel errorViewModel() {
        HomeScreenViewModel vm = new HomeScreenViewModel();
        vm.loadErrorState().set("Atlas file not found next to skin.json: /tmp/does-not-exist.atlas");
        return vm;
    }

    private static HomeScreenViewModel loadedViewModel() {
        HomeScreenViewModel vm = new HomeScreenViewModel();
        vm.skinState().set(SkinLoader.load(Path.of(
                "source.images.and.assets", "extract to your assets folder", "skin.json")));
        return vm;
    }

    private static javafx.scene.Node renderHomeScreen(HomeScreenViewModel viewModel) {
        var root = (javafx.scene.Parent) new HomeScreen(viewModel, Themes.light).render().getJavaFxNode();
        var wrapper = new javafx.scene.layout.VBox(root);
        wrapper.setPrefSize(950, 470);
        return wrapper;
    }

    private static void write(WritableImage image, String path) {
        try {
            File file = new File(path);
            file.getParentFile().mkdirs();
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("Wrote " + file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
