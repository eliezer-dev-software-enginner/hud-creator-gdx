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
