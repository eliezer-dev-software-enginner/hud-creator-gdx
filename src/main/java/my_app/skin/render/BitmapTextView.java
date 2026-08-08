package my_app.skin.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import megalodonte.base.components.Component;
import my_app.gdx.BitmapFontRenderer;
import my_app.gdx.GdxFontLoader;

import java.util.Optional;

/**
 * Renders a run of text as a {@link Canvas}, sized and drawn from the real
 * libGDX {@code BitmapFont} glyphs whenever a {@link GdxFontLoader.LoadedFont}
 * resolved for the style ({@link #BitmapTextView(Image, Optional, String, Color)}) —
 * matching the real game pixel-for-pixel instead of a substitute JavaFX system
 * font at a guessed size. Falls back to a plain {@link GraphicsContext#fillText}
 * (still on the same {@code Canvas}, so callers never need to branch on which
 * kind of node they got) when no font resolved — missing/unparseable
 * {@code .fnt}, or a style with no {@code font} field at all.
 * <p>
 * Recolors real glyphs pixel-by-pixel ({@link #tintDrawnPixels}) - draws them
 * in their native atlas color first, then multiplies each already-opaque
 * pixel's RGB (and alpha) by the target color, same as libGDX's own
 * {@code SpriteBatch} tint-before-draw. Two earlier attempts got this wrong:
 * a {@code Blend(MULTIPLY, ColorInput)} {@code Node} effect (the same
 * technique {@link DrawableView#applyTint} uses for a {@code Skin.TintedDrawable})
 * composites its opaque color layer across the effect's *entire* bounding
 * box, so every transparent gap between/around glyphs came out as a solid
 * block of the tint color (reported directly - a solid yellow rectangle
 * behind the text). Swapping to a {@code GraphicsContext} blend mode
 * ({@code SRC_ATOP}) fixed that (its alpha formula keeps only the
 * already-drawn shape opaque) but *replaces* color instead of multiplying it -
 * wrong for a font whose atlas glyphs are already pre-colored (not
 * white/grayscale meant for tinting): libGDX's real multiply tint against
 * black glyph pixels is always black regardless of the configured
 * {@code fontColor} (this exact skin declares {@code fontColor: yellow} for
 * a pre-colored-black font - the real game still renders it black, since
 * black × anything = black), but {@code SRC_ATOP} doesn't know that and
 * stamped every glyph in the configured tint instead (reported directly - the
 * text itself turned solid yellow once the background bleed was fixed).
 * Manual {@link PixelReader}/{@link PixelWriter} multiplication is the only
 * one of the three that reproduces the real engine's math exactly in every
 * case, tinted-white-glyph fonts included.
 */
public final class BitmapTextView extends Component {

    private static final double FALLBACK_FONT_SIZE = 16;
    private static final String PROPERTY_KEY = "bitmapTextView";

    private final Canvas canvas;
    private final Image atlasImage;
    private final GdxFontLoader.LoadedFont font;

    private String text;
    private Color color;

    public BitmapTextView(Image atlasImage, Optional<GdxFontLoader.LoadedFont> font, String text, Color color) {
        super(new Canvas());
        this.canvas = (Canvas) this.node;
        this.atlasImage = atlasImage;
        this.font = font.orElse(null);
        this.text = text;
        this.color = color;
        canvas.getProperties().put(PROPERTY_KEY, this);

        redraw();
    }

    public void setText(String text) {
        this.text = text;
        redraw();
    }

    public void setColor(Color color) {
        this.color = color;
        redraw();
    }

    public String text() {
        return text;
    }

    public Color color() {
        return color;
    }

    /** Whether a real {@code BitmapFont} resolved for this view, or it's drawing the plain fallback (tests only). */
    public boolean usesRealFont() {
        return font != null;
    }

    private void redraw() {
        if (font != null) {
            redrawBitmapFont();
        } else {
            redrawFallback();
        }
    }

    private void redrawBitmapFont() {
        BitmapFontData fontData = font.fontData();
        Bounds bounds = measureBounds(fontData, text);
        double width = Math.max(1, bounds.width());
        double height = Math.max(1, bounds.height());
        canvas.setWidth(width);
        canvas.setHeight(height);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        new BitmapFontRenderer(atlasImage, font.atlasRegion(), fontData).drawText(gc, text, 0, 0);
        tintDrawnPixels(gc, (int) Math.ceil(width), (int) Math.ceil(height), color);
    }

    /**
     * Multiplies every already-drawn pixel's RGB and alpha by {@code tint} -
     * exactly libGDX's own {@code SpriteBatch} tint math - leaving untouched
     * (fully transparent) pixels alone. See the class Javadoc for why this
     * needs a manual pixel pass instead of an effect or blend mode.
     */
    private static void tintDrawnPixels(GraphicsContext gc, int width, int height, Color tint) {
        Canvas canvas = gc.getCanvas();
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage drawn = canvas.snapshot(params, null);
        PixelReader reader = drawn.getPixelReader();

        WritableImage tinted = new WritableImage(width, height);
        PixelWriter writer = tinted.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = reader.getColor(x, y);
                if (c.getOpacity() == 0) continue;
                writer.setColor(x, y, Color.color(
                        c.getRed() * tint.getRed(),
                        c.getGreen() * tint.getGreen(),
                        c.getBlue() * tint.getBlue(),
                        c.getOpacity() * tint.getOpacity()));
            }
        }

        gc.clearRect(0, 0, width, height);
        gc.drawImage(tinted, 0, 0);
    }

    private record Bounds(double width, double height) {
    }

    /**
     * The canvas size needed to fit every glyph's actual drawn pixels, not
     * just the nominal cursor advance/line height - mirrors
     * {@link BitmapFontRenderer#drawText}'s own cursor math exactly (an
     * unmapped char contributes nothing, same as it's skipped there), but
     * also tracks each glyph's real right/bottom edge
     * ({@code cursorX + g.xoffset + g.width}, {@code g.yoffset + g.height}).
     * A script/cursive font routinely has a final glyph whose flourish
     * extends past its own {@code xadvance} (there's no next character to
     * "make room" for) - sizing the canvas from advance-sum/lineHeight alone
     * silently clipped that overhang at the canvas edge (reported directly,
     * against a screenshot of the real game rendering it in full). Width
     * still falls back to the full advance sum when that's larger (e.g. a
     * trailing space has no pixels of its own but should still reserve its
     * width); height still falls back to the nominal {@code lineHeight} for
     * the same reason.
     */
    private static Bounds measureBounds(BitmapFontData fontData, String text) {
        double cursorX = 0;
        double maxRight = 0;
        double maxBottom = fontData.lineHeight;
        for (int i = 0; i < text.length(); i++) {
            Glyph g = fontData.getGlyph(text.charAt(i));
            if (g == null) continue;
            maxRight = Math.max(maxRight, cursorX + g.xoffset + g.width);
            maxBottom = Math.max(maxBottom, g.yoffset + g.height);
            cursorX += g.xadvance;
        }
        return new Bounds(Math.max(cursorX, maxRight), maxBottom);
    }

    private void redrawFallback() {
        Font fxFont = Font.font(FALLBACK_FONT_SIZE);
        Text measure = new Text(text);
        measure.setFont(fxFont);
        double width = Math.max(1, measure.getLayoutBounds().getWidth());
        double height = Math.max(1, measure.getLayoutBounds().getHeight());
        canvas.setWidth(width);
        canvas.setHeight(height);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        gc.setFont(fxFont);
        gc.setFill(color);
        gc.setTextBaseline(VPos.TOP);
        gc.fillText(text, 0, 0);
    }

    /** The canvas's own current width/height (tests only - real callers read the node's {@code prefWidth/prefHeight} like every other widget kind). */
    public Canvas canvas() {
        return canvas;
    }

    /** Recovers the live {@link BitmapTextView} behind a {@link Canvas} node found in a built scene graph (e.g. a {@code StackPane} child) - the built node tree only ever holds the raw {@code Canvas}, not this wrapper, so callers that need to call {@link #setText}/{@link #setColor} later look it back up this way. Null for any other node, or a {@code Canvas} not created by this class. */
    public static BitmapTextView of(Node node) {
        if (node instanceof Canvas c && c.getProperties().get(PROPERTY_KEY) instanceof BitmapTextView view) {
            return view;
        }
        return null;
    }
}
