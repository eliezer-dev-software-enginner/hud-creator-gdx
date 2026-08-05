package my_app.skin.render;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import my_app.skin.AtlasRegion;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Manual visual check for Fase 2 (skin region rendering) — not a JUnit test,
 * since there's nothing meaningful to assert about pixel output
 * automatically. Run via the {@code skinPreviewSnapshot} Gradle task, then
 * look at the generated {@code build/skin-preview-smoke-test.png}: plain
 * regions on top, 9-patch buttons (native size + stretched) on the bottom row.
 */
public class SkinPreviewSnapshot {

    public static void main(String[] args) throws Exception {
        SkinModel skin = SkinLoader.load(Path.of(
                "source.images.and.assets", "extract to your assets folder", "skin.json"));

        new JFXPanel(); // starts the JavaFX toolkit without needing an Application/Stage

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                renderAndSave(skin);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void renderAndSave(SkinModel skin) throws IOException {
        Image atlasImage = SkinImages.loadAtlasImage(skin);

        HBox plainRow = new HBox(12);
        for (String name : List.of("arrow", "play-up", "icon-volume-up")) {
            AtlasRegion region = skin.region(name).orElseThrow();
            plainRow.getChildren().add(new AtlasImageView(atlasImage, region).imageView());
        }

        HBox ninePatchRow = new HBox(12);
        AtlasRegion buttonUp = skin.region("button-up").orElseThrow();
        AtlasRegion buttonChecked = skin.region("button-checked").orElseThrow();
        ninePatchRow.getChildren().addAll(
                new NinePatchView(atlasImage, buttonUp).getNode(),                // native size
                new NinePatchView(atlasImage, buttonUp).size(300, 120).getNode(), // stretched
                new NinePatchView(atlasImage, buttonChecked).size(220, 60).getNode()
        );

        VBox root = new VBox(20, plainRow, ninePatchRow);
        root.setStyle("-fx-background-color: #444444;");
        root.setPadding(new Insets(20));

        new Scene(root); // forces a CSS/layout pass that snapshot() relies on

        WritableImage snapshot = root.snapshot(new SnapshotParameters(), null);
        File outputFile = new File("build/skin-preview-smoke-test.png");
        outputFile.getParentFile().mkdirs();
        ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", outputFile);
        System.out.println("Wrote " + outputFile.getAbsolutePath());
    }
}
