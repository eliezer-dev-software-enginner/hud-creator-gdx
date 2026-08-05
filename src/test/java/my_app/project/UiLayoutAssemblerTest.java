package my_app.project;

import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UiLayoutAssemblerTest {

    @Test
    void assemblesOneDtoPerWidgetType() {
        List<PlacedWidget> widgets = List.of(
                new PlacedWidget("widget-1", new WidgetSpec.ImageSpec("arrow"), 1, 2, null),
                new PlacedWidget("widget-2", new WidgetSpec.ButtonSpec("default"), 3, 4, null),
                new PlacedWidget("widget-3", new WidgetSpec.TextButtonSpec("default", "Play"), 5, 6, "play"),
                new PlacedWidget("widget-4", new WidgetSpec.LabelSpec("default", "Lives"), 7, 8, null)
        );

        UiLayout layout = UiLayoutAssembler.assemble("skin/skin.json", 640, 360, "bg/reference.png", widgets);

        assertEquals(1, layout.formatVersion());
        assertEquals("skin/skin.json", layout.skinPath());
        assertEquals(640, layout.canvasWidth());
        assertEquals(360, layout.canvasHeight());
        assertEquals("bg/reference.png", layout.backgroundImagePath());
        assertEquals(4, layout.widgets().size());

        PlacedWidgetDto image = layout.widgets().get(0);
        assertEquals("image", image.type());
        assertEquals("arrow", image.regionName());
        assertNull(image.styleName());
        assertNull(image.text());
        assertEquals(1, image.x());
        assertEquals(2, image.y());

        PlacedWidgetDto button = layout.widgets().get(1);
        assertEquals("button", button.type());
        assertEquals("default", button.styleName());
        assertNull(button.regionName());
        assertNull(button.text());

        PlacedWidgetDto textButton = layout.widgets().get(2);
        assertEquals("textButton", textButton.type());
        assertEquals("default", textButton.styleName());
        assertEquals("Play", textButton.text());
        assertEquals("play", textButton.nickname());

        PlacedWidgetDto label = layout.widgets().get(3);
        assertEquals("label", label.type());
        assertEquals("default", label.styleName());
        assertEquals("Lives", label.text());
        assertNull(label.nickname());
    }

    @Test
    void fromDtoIsTheInverseOfToDto() {
        PlacedWidget original = new PlacedWidget(
                "widget-3", new WidgetSpec.TextButtonSpec("default", "Play"), 5, 6, "play");

        UiLayout layout = UiLayoutAssembler.assemble("skin/skin.json", 640, 360, null, List.of(original));
        PlacedWidget roundTripped = UiLayoutAssembler.fromDto(layout.widgets().get(0));

        assertEquals(original, roundTripped);
    }

    @Test
    void relativeSkinPathPointsThroughASubfolder(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("hud.json");
        Path skinJson = tempDir.resolve("skin/skin.json");
        Files.createDirectories(skinJson.getParent());
        Files.writeString(skinJson, "{}");

        assertEquals("skin/skin.json", UiLayoutAssembler.relativeSkinPath(skinJson, outputFile));
    }

    @Test
    void relativeSkinPathInSameDirectoryIsJustTheFileName(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("hud.json");
        Path skinJson = tempDir.resolve("skin.json");
        Files.writeString(skinJson, "{}");

        assertEquals("skin.json", UiLayoutAssembler.relativeSkinPath(skinJson, outputFile));
    }

    @Test
    void relativePathWorksForAnyFileNotJustTheSkin(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("hud.json");
        Path backgroundImage = tempDir.resolve("bg/reference.png");
        Files.createDirectories(backgroundImage.getParent());
        Files.writeString(backgroundImage, "not a real png, doesn't matter for this test");

        assertEquals("bg/reference.png", UiLayoutAssembler.relativePath(backgroundImage, outputFile));
    }
}
