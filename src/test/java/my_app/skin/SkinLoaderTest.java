package my_app.skin;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinLoaderTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @Test
    void loadsRegionsFromAtlas() {
        SkinModel skin = SkinLoader.load(EXAMPLE_SKIN);

        AtlasRegion arrow = skin.region("arrow").orElseThrow();
        assertEquals(269, arrow.x());
        assertEquals(331, arrow.y());
        assertEquals(17, arrow.width());
        assertEquals(20, arrow.height());
        assertTrue(!arrow.isNinePatch());

        AtlasRegion button = skin.region("button-checked").orElseThrow();
        assertTrue(button.isNinePatch());
        assertEquals(4, button.splits().length);
        assertEquals(14, button.splits()[0]);
        assertEquals(16, button.splits()[1]);
        assertEquals(18, button.splits()[2]);
        assertEquals(13, button.splits()[3]);
    }

    @Test
    void resolvesAtlasImageNextToAtlasFile() {
        SkinModel skin = SkinLoader.load(EXAMPLE_SKIN);

        assertTrue(skin.atlasImagePath().toString().endsWith("skin.png"));
        assertTrue(skin.atlasImagePath().toFile().isFile());
    }

    @Test
    void parsesColors() {
        SkinModel skin = SkinLoader.load(EXAMPLE_SKIN);

        SkinColor white = skin.color("white").orElseThrow();
        assertEquals(1, white.r());
        assertEquals(1, white.g());
        assertEquals(1, white.b());
        assertEquals(1, white.a());
    }

    @Test
    void parsesButtonStyles() {
        SkinModel skin = SkinLoader.load(EXAMPLE_SKIN);

        assertTrue(skin.styleClasses().contains("ButtonStyle"));
        assertTrue(skin.styleNames("ButtonStyle").containsAll(java.util.List.of("default", "toggle", "play")));

        var defaultStyle = skin.style("ButtonStyle", "default").orElseThrow();
        assertEquals("button-up", defaultStyle.get("up"));
        assertEquals("button-down", defaultStyle.get("down"));
    }

    @Test
    void parsesTextButtonStyleWithFontReference() {
        SkinModel skin = SkinLoader.load(EXAMPLE_SKIN);

        var textButton = skin.style("TextButtonStyle", "default").orElseThrow();
        assertEquals("font", textButton.get("font"));
        assertEquals("lightgray", textButton.get("fontColor"));

        assertTrue(skin.fontFile("font").isPresent());
        assertTrue(skin.fontFile("font").get().endsWith(".fnt"));
    }
}
