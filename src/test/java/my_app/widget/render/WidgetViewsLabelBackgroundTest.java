package my_app.widget.render;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.layout.StackPane;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import my_app.skin.render.SkinImages;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code LabelStyle} can declare an optional {@code background} drawable -
 * real Scene2D {@code Label} draws it behind the text (e.g. a bordered/framed
 * style). {@link WidgetViews#buildLabel} never resolved this field at all, so
 * a Label using one of these styles previewed as bare floating text with no
 * box, while the real libGDX game (which builds an actual {@code Label} off
 * the same {@code LabelStyle}) correctly drew the frame - reported directly,
 * against a screenshot comparing the two ({@code gdx-skins/comic}'s "alt"
 * style, which declares {@code background: window}).
 */
class WidgetViewsLabelBackgroundTest {

    private static final Path COMIC_SKIN = Path.of(
            "..", "gdx-skins", "comic", "skin", "comic-ui.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void aStyleWithABackgroundGetsOne() throws Exception {
        runOnFxThreadAndWait(() -> {
            SkinModel skin = SkinLoader.load(COMIC_SKIN);
            StackPane node = (StackPane) WidgetViews.build(
                    skin, SkinImages.loadAtlasImage(skin),
                    new WidgetSpec.LabelSpec("alt", "New game")
            ).getNode();

            assertEquals(2, node.getChildren().size(), "background drawable + the text canvas");
            assertTrue(WidgetViews.isResizable(node), "a real background drawable should be resizable, same as a TextButton's");
        });
    }

    @Test
    void aStyleWithNoBackgroundStaysJustTheText() throws Exception {
        runOnFxThreadAndWait(() -> {
            SkinModel skin = SkinLoader.load(COMIC_SKIN);
            StackPane node = (StackPane) WidgetViews.build(
                    skin, SkinImages.loadAtlasImage(skin),
                    new WidgetSpec.LabelSpec("default", "Score")
            ).getNode();

            assertEquals(1, node.getChildren().size(), "no background field on this style - just the text canvas");
            assertFalse(WidgetViews.isResizable(node), "nothing to resize but an invisible wrapper box");
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
