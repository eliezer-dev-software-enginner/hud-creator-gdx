package my_app.skin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A real Skin Composer export (unquoted keys/string values — libGDX's own
 * lenient JSON dialect, not strict JSON) failed to load with
 * {@code com.fasterxml.jackson.core.JsonParseException: Unexpected character
 * ('c' ...): was expecting double-quote to start field name} back when this
 * parser used Jackson. Locks in the fix (libGDX's own {@code JsonReader}
 * instead) against exactly that shape of input — quoted keys/values still
 * work too (that's what {@link SkinLoaderTest} already covers against the
 * bundled example skin), lenient is a superset.
 */
class SkinJsonParserTest {

    @Test
    void parsesLenientUnquotedSkinJson(@TempDir Path tempDir) throws IOException {
        // Same shape as a real Skin Composer export that previously failed to load.
        String relaxedJson = """
                {
                com.badlogic.gdx.scenes.scene2d.ui.Button$ButtonStyle: {
                	default: {
                		up: button-up
                		down: button-down
                		over: button-over
                		focused: button-focused
                		disabled: button-disable
                	}
                }
                }""";
        Path jsonFile = tempDir.resolve("btn-skin.json");
        Files.writeString(jsonFile, relaxedJson);

        var parsed = SkinJsonParser.parse(jsonFile);

        var defaultStyle = parsed.styles().get("ButtonStyle").get("default");
        assertEquals("button-up", defaultStyle.get("up"));
        assertEquals("button-down", defaultStyle.get("down"));
        assertEquals("button-over", defaultStyle.get("over"));
        assertEquals("button-focused", defaultStyle.get("focused"));
        assertEquals("button-disable", defaultStyle.get("disabled"));
    }

    @Test
    void parsesTintedDrawableAliasesWithANamedColor(@TempDir Path tempDir) throws IOException {
        // Same shape as the gdx-skins "flat-earth" pack: a TintedDrawable
        // reuses "button-close"'s art, recolored, under the alias "button-close-c".
        // SkinJsonParser used to silently skip this whole class, which is what
        // caused WidgetViews to throw "Region not found: button-close-c".
        String relaxedJson = """
                {
                com.badlogic.gdx.graphics.Color: {
                	color: { r: 1, g: 0, b: 0, a: 1 }
                }
                com.badlogic.gdx.scenes.scene2d.ui.Skin$TintedDrawable: {
                	button-close-c: {
                		name: button-close
                		color: color
                	}
                }
                }""";
        Path jsonFile = tempDir.resolve("flat-earth-ui.json");
        Files.writeString(jsonFile, relaxedJson);

        var parsed = SkinJsonParser.parse(jsonFile);

        TintedDrawableRef ref = parsed.tintedDrawables().get("button-close-c");
        assertEquals("button-close", ref.regionName());
        assertEquals(1, ref.tint().r());
        assertEquals(0, ref.tint().g());
    }

    @Test
    void parsesTintedDrawableAliasesWithAnInlineColor(@TempDir Path tempDir) throws IOException {
        // Same shape as the gdx-skins "plain-james" pack: the color is given
        // as an inline {r, g, b, a} literal instead of a name reference.
        // SkinJsonParser used to only support the name-reference form, so
        // this shape was silently dropped (colorName ended up null), which
        // caused WidgetViews to throw "Region not found: round-dark-gray".
        String relaxedJson = """
                {
                com.badlogic.gdx.scenes.scene2d.ui.Skin$TintedDrawable: {
                	round-dark-gray: {
                		name: round-white
                		color: {
                			r: 0.22
                			g: 0.22
                			b: 0.22
                			a: 1
                		}
                	}
                }
                }""";
        Path jsonFile = tempDir.resolve("plain-james-ui.json");
        Files.writeString(jsonFile, relaxedJson);

        var parsed = SkinJsonParser.parse(jsonFile);

        TintedDrawableRef ref = parsed.tintedDrawables().get("round-dark-gray");
        assertEquals("round-white", ref.regionName());
        assertEquals(0.22, ref.tint().r());
        assertEquals(1, ref.tint().a());
    }

    @Test
    void parsesStrictQuotedSkinJsonToo(@TempDir Path tempDir) throws IOException {
        String strictJson = """
                {
                  "com.badlogic.gdx.graphics.Color": {
                    "white": { "r": 1, "g": 1, "b": 1, "a": 1 }
                  },
                  "com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle": {
                    "default": { "fontColor": "white" }
                  }
                }""";
        Path jsonFile = tempDir.resolve("skin.json");
        Files.writeString(jsonFile, strictJson);

        var parsed = SkinJsonParser.parse(jsonFile);

        assertEquals(1, parsed.colors().get("white").r());
        assertEquals("white", parsed.styles().get("LabelStyle").get("default").get("fontColor"));
    }
}
