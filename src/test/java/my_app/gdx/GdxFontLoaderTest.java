package my_app.gdx;

import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GdxFontLoader} loads real libGDX parsing classes ({@code TextureAtlasData},
 * {@code BitmapFontData}) standalone, outside any {@code Gdx.app} context - these
 * lock in that it resolves the declared glyph region and produces usable glyph
 * data, and specifically that {@code flip=true} is used (not {@code false}):
 * libGDX's own javadoc says {@code flip=true} is for "a perspective where 0,0
 * is the upper left corner", which is exactly what {@link BitmapFontRenderer}
 * needs to place glyphs directly as Canvas (0,0 top-left) coordinates - with
 * {@code flip=false} (OpenGL's Y-up convention), {@code Glyph.yoffset} comes
 * out negative and glyphs land in the wrong place.
 */
class GdxFontLoaderTest {

    private static final Path SKIN_DIR = Path.of(
            "..", "gdx-skins", "arcade", "skin");
    private static final Path ATLAS = SKIN_DIR.resolve("arcade-ui.atlas");
    private static final Path FONT = SKIN_DIR.resolve("title-export.fnt");

    @Test
    void resolvesTheDeclaredGlyphRegionFromTheAtlas() {
        GdxFontLoader.LoadedFont loaded = GdxFontLoader.load(ATLAS, FONT, "title-export");

        assertNotNull(loaded.atlasRegion());
        assertEquals("title-export", loaded.atlasRegion().name);
        assertTrue(loaded.atlasRegion().width > 0);
        assertTrue(loaded.atlasRegion().height > 0);
    }

    @Test
    void parsesUsableGlyphData() {
        GdxFontLoader.LoadedFont loaded = GdxFontLoader.load(ATLAS, FONT, "title-export");
        BitmapFontData fontData = loaded.fontData();

        assertTrue(fontData.lineHeight > 0);
        Glyph a = fontData.getGlyph('A');
        assertNotNull(a, "capital A should be present in a title font");
        assertTrue(a.width > 0);
        assertTrue(a.height > 0);
        assertTrue(a.xadvance > 0);
    }

    @Test
    void loadsWithFlipTrueSoGlyphYoffsetIsTopRelativeNotOpenGlYUp() {
        GdxFontLoader.LoadedFont loaded = GdxFontLoader.load(ATLAS, FONT, "title-export");
        BitmapFontData fontData = loaded.fontData();

        assertTrue(fontData.flipped, "must load with flip=true - Canvas drawing needs top-relative yoffset, "
                + "flip=false yields libGDX's own OpenGL Y-up (negative) convention instead");

        // With flip=true every real glyph's top-relative yoffset is >= 0 - the
        // top of the line box is y=0, nothing draws above it. flip=false would
        // instead produce large *negative* values here (confirmed empirically
        // against this exact file before fixing GdxFontLoader).
        for (char c : new char[]{'H', 'e', 'l', 'o', 'A', 'g', 'y'}) {
            Glyph g = fontData.getGlyph(c);
            if (g == null) continue;
            assertTrue(g.yoffset >= 0, "'" + c + "' yoffset should be top-relative (>=0), was " + g.yoffset);
        }
    }

    @Test
    void anUnknownRegionNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> GdxFontLoader.load(ATLAS, FONT, "does-not-exist"));
    }
}
