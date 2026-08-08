package my_app.skin.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.paint.Color;
import my_app.gdx.GdxFontLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link BitmapTextView} draws real {@code BitmapFont} glyphs onto a
 * {@code Canvas} sized to exactly fit them - these verify the computed
 * state ({@code Canvas} width/height, stored text/color) rather than any
 * rendered pixel output, per this project's standing "no visual/pixel
 * checks" rule.
 */
class BitmapTextViewTest {

    private static final Path SKIN_DIR = Path.of(
            "..", "gdx-skins", "arcade", "skin");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    private static GdxFontLoader.LoadedFont loadTitleFont() {
        return GdxFontLoader.load(
                SKIN_DIR.resolve("arcade-ui.atlas"),
                SKIN_DIR.resolve("title-export.fnt"),
                "title-export");
    }

    @Test
    void sizesTheCanvasFromRealGlyphMetrics() throws Exception {
        runOnFxThreadAndWait(() -> {
            GdxFontLoader.LoadedFont font = loadTitleFont();
            BitmapFontData fontData = font.fontData();

            BitmapTextView view = new BitmapTextView(dummyImage(), Optional.of(font), "Hi", Color.WHITE);

            assertEquals(measureWidth(fontData, "Hi"), view.canvas().getWidth(), 0.01);
            assertEquals(measureHeight(fontData, "Hi"), view.canvas().getHeight(), 0.01);
            assertTrue(view.usesRealFont());
        });
    }

    @Test
    void sizesToFitAGlyphThatOverhangsItsOwnAdvance() throws Exception {
        runOnFxThreadAndWait(() -> {
            // title-export's capital H (bottom = yoffset+height = 73) draws
            // taller than the font's own nominal lineHeight (58) - a script
            // font's final glyph can just as easily overhang its own
            // xadvance horizontally. The canvas must fit the real drawn
            // pixels, not just the nominal cursor advance/line height, or
            // the last glyph's overhang gets silently clipped at the edge
            // (reported directly, against a real-game screenshot rendering
            // it in full).
            GdxFontLoader.LoadedFont font = loadTitleFont();
            BitmapFontData fontData = font.fontData();
            assumeTrue(fontData.getGlyph('H') != null
                            && fontData.getGlyph('H').yoffset + fontData.getGlyph('H').height > fontData.lineHeight,
                    "this regression needs a font whose 'H' overhangs its own lineHeight");

            BitmapTextView view = new BitmapTextView(dummyImage(), Optional.of(font), "Hi", Color.WHITE);

            assertTrue(view.canvas().getHeight() > fontData.lineHeight,
                    "canvas should grow past the nominal lineHeight to fit H's real extent");
            assertEquals(measureHeight(fontData, "Hi"), view.canvas().getHeight(), 0.01);
        });
    }

    @Test
    void setTextResizesTheCanvasToTheNewStringsWidth() throws Exception {
        runOnFxThreadAndWait(() -> {
            GdxFontLoader.LoadedFont font = loadTitleFont();
            BitmapFontData fontData = font.fontData();

            BitmapTextView view = new BitmapTextView(dummyImage(), Optional.of(font), "Hi", Color.WHITE);
            view.setText("Hello Arcade");

            assertEquals("Hello Arcade", view.text());
            assertEquals(measureWidth(fontData, "Hello Arcade"), view.canvas().getWidth(), 0.01);
            assertEquals(measureHeight(fontData, "Hello Arcade"), view.canvas().getHeight(), 0.01);
        });
    }

    @Test
    void setColorUpdatesTheStoredColorWithoutResizing() throws Exception {
        runOnFxThreadAndWait(() -> {
            GdxFontLoader.LoadedFont font = loadTitleFont();
            BitmapTextView view = new BitmapTextView(dummyImage(), Optional.of(font), "Hi", Color.WHITE);
            double widthBefore = view.canvas().getWidth();
            double heightBefore = view.canvas().getHeight();

            view.setColor(Color.RED);

            assertEquals(Color.RED, view.color());
            assertEquals(widthBefore, view.canvas().getWidth(), 0.01);
            assertEquals(heightBefore, view.canvas().getHeight(), 0.01);
        });
    }

    @Test
    void fallsBackToAPlainRenderingWhenNoFontResolved() throws Exception {
        runOnFxThreadAndWait(() -> {
            BitmapTextView view = new BitmapTextView(dummyImage(), Optional.empty(), "Play", Color.BLACK);

            assertFalse(view.usesRealFont());
            assertTrue(view.canvas().getWidth() > 0, "fallback should still produce a usable size");
            assertTrue(view.canvas().getHeight() > 0);
            assertEquals("Play", view.text());
        });
    }

    /** Mirrors {@link BitmapTextView}'s own bounds computation independently - the real right edge of every glyph, or the full advance sum if that's larger (a trailing space has no pixels but should still reserve its width). */
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

    /** {@link BitmapTextView} only reads {@code atlasImage} pixels when actually drawing - a 1x1 placeholder is enough for these size/state assertions. */
    private static javafx.scene.image.Image dummyImage() {
        return new javafx.scene.image.WritableImage(1, 1);
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
