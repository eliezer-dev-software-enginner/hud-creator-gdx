package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.layout_components.Canva;
import megalodonte.theme.DefaultTheme;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Manual visual check for Fase 4's {@link CanvasController} — not a JUnit
 * test. Calls {@link CanvasController#place} directly instead of simulating
 * a real OS-level drag gesture (JavaFX has no simple API for that outside a
 * Robot-driven framework like TestFX), so this exercises exactly the part
 * that's new and risky: center-on-drop math, clamping to canvas bounds, and
 * {@code ListState} bookkeeping — the same code the real drag-and-drop
 * handler in {@link HomeScreen} calls into.
 * <p>
 * Run via the {@code canvasControllerSnapshot} Gradle task, then look at
 * {@code build/canvas-controller-smoke-test.png}: widgets dropped at
 * different points, including one intentionally dropped past the
 * bottom-right corner to show clamping keeps it fully inside the canvas.
 */
public class CanvasControllerSmokeTest {

    public static void main(String[] args) throws Exception {
        new JFXPanel();

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
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        viewModel.canvasWidthState().set(400);
        viewModel.canvasHeightState().set(240);
        viewModel.skinState().set(SkinLoader.load(Path.of(
                "source.images.and.assets", "extract to your assets folder", "skin.json")));

        Canva canva = new Canva();
        Region pane = (Region) canva.getNode();
        pane.setMinSize(400, 240);
        pane.setPrefSize(400, 240);
        pane.setMaxSize(400, 240);
        pane.setStyle("-fx-background-color: white; -fx-border-color: #999999; -fx-border-width: 1;");

        CanvasController controller = new CanvasController(canva, viewModel);
        controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
        controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 200, 120);
        controller.place(new WidgetSpec.ImageSpec("arrow"), 350, 200);
        // Intentionally past the bottom-right corner - clamping should keep it fully inside instead of spilling over.
        controller.place(new WidgetSpec.ButtonSpec("toggle"), 500, 400);

        System.out.println("Placed widgets: " + viewModel.placedWidgets().get());

        new Scene(pane);
        WritableImage snapshot = pane.snapshot(new SnapshotParameters(), null);
        write(snapshot, "build/canvas-controller-smoke-test.png");
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
