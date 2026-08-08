package my_app.widget.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import my_app.skin.render.BitmapTextView;
import my_app.skin.render.SkinImages;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JavaFX preview text used to sit at the platform default (~13px), then at a
 * plain {@code Text} sized off the {@code .fnt} file's {@code lineHeight} -
 * neither actually drew the skin's own glyphs. This verifies {@link WidgetViews}
 * now builds a {@link BitmapTextView} that draws the real {@code BitmapFont}
 * glyphs and sizes itself to fit their real drawn extent - computed
 * independently against the same loaded font data (not a hardcoded pixel
 * count) by walking every {@link Glyph}'s own offset/advance/size fields.
 */
class WidgetViewsFontSizeTest {

    private static final Path GLASSY_SKIN = Path.of(
            "..", "gdx-skins", "glassy", "skin", "glassy-ui.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void textButtonSizesFromTheRealBitmapFontGlyphs() throws Exception {
        runOnFxThreadAndWait(() -> {
            SkinModel skin = SkinLoader.load(GLASSY_SKIN);
            StackPane node = (StackPane) WidgetViews.build(
                    skin,
                    SkinImages.loadAtlasImage(skin),
                    new WidgetSpec.TextButtonSpec("default", "Play")
            ).getNode();
            new Scene(node);

            BitmapTextView textView = WidgetViews.bitmapTextViewOf(node);
            assertTrue(textView.usesRealFont(), "glassy's TextButtonStyle.default declares font-big - should resolve, not fall back");

            BitmapFontData fontData = skin.font("font-big").orElseThrow().fontData();
            assertEquals(60, fontData.lineHeight, 0.01, "glassy's font-big has lineHeight=60");
            assertEquals(measureHeight(fontData, "Play"), textView.canvas().getHeight(), 0.01);
            assertEquals(measureWidth(fontData, "Play"), textView.canvas().getWidth(), 0.01);
        });
    }

    @Test
    void labelSizesFromTheStylesOwnFont() throws Exception {
        runOnFxThreadAndWait(() -> {
            SkinModel skin = SkinLoader.load(GLASSY_SKIN);
            StackPane node = (StackPane) WidgetViews.build(
                    skin,
                    SkinImages.loadAtlasImage(skin),
                    new WidgetSpec.LabelSpec("big", "Score")
            ).getNode();
            new Scene(node);

            BitmapTextView textView = WidgetViews.bitmapTextViewOf(node);
            assertTrue(textView.usesRealFont(), "glassy's LabelStyle.big declares font-big - should resolve, not fall back");

            BitmapFontData fontData = skin.font("font-big").orElseThrow().fontData();
            assertEquals(measureHeight(fontData, "Score"), textView.canvas().getHeight(), 0.01);
            assertEquals(measureWidth(fontData, "Score"), textView.canvas().getWidth(), 0.01);
        });
    }

    /** Mirrors {@link BitmapTextView}'s own bounds computation independently - the real right edge of every glyph, or the full advance sum if that's larger. */
    private static double measureWidth(BitmapFontData fontData, String text) {
        double cursorX = 0;
        double maxRight = 0;
        for (char c : text.toCharArray()) {
            Glyph g = fontData.getGlyph(c);
            if (g == null) continue;
            maxRight = Math.max(maxRight, cursorX + g.xoffset + g.width);
            cursorX += g.xadvance;
        }
        return Math.max(cursorX, maxRight);
    }

    /** Mirrors {@link BitmapTextView}'s own bounds computation independently - the real bottom edge of every glyph, or the nominal lineHeight if that's larger. */
    private static double measureHeight(BitmapFontData fontData, String text) {
        double maxBottom = fontData.lineHeight;
        for (char c : text.toCharArray()) {
            Glyph g = fontData.getGlyph(c);
            if (g == null) continue;
            maxBottom = Math.max(maxBottom, g.yoffset + g.height);
        }
        return maxBottom;
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
