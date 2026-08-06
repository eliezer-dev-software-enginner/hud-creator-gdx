package my_app.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiLayoutWriterTest {

    @Test
    void writesReadableJsonWithExpectedShape(@TempDir Path tempDir) throws Exception {
        List<PlacedWidget> widgets = List.of(
                new PlacedWidget("widget-1", new WidgetSpec.ButtonSpec("default"), 10, 20, null, null, null)
        );
        UiLayout layout = UiLayoutAssembler.assemble("skin/skin.json", 640, 360, null, widgets);

        Path outputFile = tempDir.resolve("nested/hud.json");
        UiLayoutWriter.write(layout, outputFile);

        assertTrue(Files.isRegularFile(outputFile), "write() should create missing parent directories");

        JsonNode root = new ObjectMapper().readTree(outputFile.toFile());
        assertEquals(1, root.get("formatVersion").asInt());
        assertEquals("skin/skin.json", root.get("skinPath").asText());
        assertEquals(640, root.get("canvasWidth").asInt());
        assertEquals(360, root.get("canvasHeight").asInt());
        // Not set in this test - shouldn't be written at all.
        assertFalse(root.has("backgroundImagePath"));

        JsonNode widget = root.get("widgets").get(0);
        assertEquals("button", widget.get("type").asText());
        assertEquals("default", widget.get("styleName").asText());
        assertEquals(10, widget.get("x").asDouble());
        assertEquals(20, widget.get("y").asDouble());
        // Fields that don't apply to a button (regionName/text) or aren't set (nickname) shouldn't be written at all.
        assertFalse(widget.has("regionName"));
        assertFalse(widget.has("text"));
        assertFalse(widget.has("nickname"));
    }

    @Test
    void writesBackgroundImagePathWhenSet(@TempDir Path tempDir) throws Exception {
        UiLayout layout = UiLayoutAssembler.assemble("skin/skin.json", 640, 360, "bg/reference.png", List.of());

        Path outputFile = tempDir.resolve("hud.json");
        UiLayoutWriter.write(layout, outputFile);

        JsonNode root = new ObjectMapper().readTree(outputFile.toFile());
        assertEquals("bg/reference.png", root.get("backgroundImagePath").asText());
    }
}
