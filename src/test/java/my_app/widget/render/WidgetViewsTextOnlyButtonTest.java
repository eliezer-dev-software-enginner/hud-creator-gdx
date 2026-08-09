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

/**
 * {@code TextButtonStyle.up} is optional in real libGDX too - a text-only
 * button (no background image, just colored text, e.g. a hyperlink style) is
 * a valid style some skins declare (this one: {@code gdx-skins/terra-mother}'s
 * {@code TextButtonStyle.default} has only a {@code font} field, no
 * {@code up}/{@code down}/{@code over} at all). {@link WidgetViews#buildTextButton}
 * required {@code up} unconditionally and threw {@code IllegalArgumentException}
 * for every one of these - found by sweeping every bundled skin
 * ({@link my_app.AllSkinsSmokeTest}), not from a single reproduction case.
 */
class WidgetViewsTextOnlyButtonTest {

    private static final Path TERRA_MOTHER_SKIN = Path.of(
            "..", "gdx-skins", "terra-mother", "skin", "terra-mother-ui.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void aStyleWithNoUpDrawableBuildsJustTheText() throws Exception {
        runOnFxThreadAndWait(() -> {
            SkinModel skin = SkinLoader.load(TERRA_MOTHER_SKIN);
            StackPane node = (StackPane) WidgetViews.build(
                    skin, SkinImages.loadAtlasImage(skin),
                    new WidgetSpec.TextButtonSpec("default", "Test")
            ).getNode();

            assertEquals(1, node.getChildren().size(), "no \"up\" field on this style - just the text canvas, no background");
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
