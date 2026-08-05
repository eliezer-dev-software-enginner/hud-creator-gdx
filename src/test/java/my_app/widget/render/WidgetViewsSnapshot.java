package my_app.widget.render;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import my_app.skin.render.SkinImages;
import my_app.widget.WidgetSpec;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Manual visual check for Fase 4's {@link WidgetViews} — not a JUnit test.
 * Run via the {@code widgetViewsSnapshot} Gradle task, then look at
 * {@code build/widget-views-smoke-test.png}: two Button styles, a
 * TextButton, and a plain Image region, side by side.
 */
public class WidgetViewsSnapshot {

    public static void main(String[] args) throws Exception {
        new JFXPanel();

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                renderAndSave();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void renderAndSave() {
        SkinModel skin = SkinLoader.load(Path.of(
                "source.images.and.assets", "extract to your assets folder", "skin.json"));
        Image atlasImage = SkinImages.loadAtlasImage(skin);

        HBox row = new HBox(16,
                WidgetViews.build(skin, atlasImage, new WidgetSpec.ButtonSpec("default")).getNode(),
                WidgetViews.build(skin, atlasImage, new WidgetSpec.ButtonSpec("toggle")).getNode(),
                WidgetViews.build(skin, atlasImage, new WidgetSpec.TextButtonSpec("default", "Play")).getNode(),
                WidgetViews.build(skin, atlasImage, new WidgetSpec.ImageSpec("icon-volume-up")).getNode()
        );
        row.setStyle("-fx-background-color: #444444; -fx-padding: 16;");

        new Scene(row);
        WritableImage snapshot = row.snapshot(new SnapshotParameters(), null);
        write(snapshot, "build/widget-views-smoke-test.png");
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
