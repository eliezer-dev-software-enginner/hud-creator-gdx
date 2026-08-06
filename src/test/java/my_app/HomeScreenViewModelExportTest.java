package my_app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import my_app.skin.SkinLoader;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link HomeScreenViewModel#saveTo} / {@link HomeScreenViewModel#exportTo}
 * directly — the parts of Save/Export after the (untestable) native
 * {@code FileChooser} dialog — end to end against the real example skin.
 */
class HomeScreenViewModelExportTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @Test
    void exportToWritesJsonAndCopiesSkinAlongside(@TempDir Path tempDir) throws Exception {
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        viewModel.canvasWidthState().set(640);
        viewModel.canvasHeightState().set(360);
        viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
        viewModel.placedWidgets().add(new PlacedWidget("widget-1", new WidgetSpec.ButtonSpec("default"), 4.5, 18.5, null, null, null));

        Path outputFile = tempDir.resolve("ui/hud.json");
        viewModel.exportTo(outputFile);

        assertTrue(viewModel.statusMessageState().get().startsWith("UI exported to"),
                "unexpected status: " + viewModel.statusMessageState().get());

        JsonNode root = new ObjectMapper().readTree(outputFile.toFile());
        assertEquals("skin/skin.json", root.get("skinPath").asText());
        assertEquals(640, root.get("canvasWidth").asInt());
        assertEquals("button", root.get("widgets").get(0).get("type").asText());

        Path skinDir = outputFile.getParent().resolve("skin");
        assertTrue(skinDir.resolve("skin.json").toFile().isFile());
        assertTrue(skinDir.resolve("skin.atlas").toFile().isFile());
        assertTrue(skinDir.resolve("skin.png").toFile().isFile());
    }

    @Test
    void saveToWritesJsonWithoutCopyingSkin(@TempDir Path tempDir) throws Exception {
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        viewModel.canvasWidthState().set(320);
        viewModel.canvasHeightState().set(180);
        viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

        Path outputFile = tempDir.resolve("project.json");
        viewModel.saveTo(outputFile);

        assertTrue(viewModel.statusMessageState().get().startsWith("Project saved to"),
                "unexpected status: " + viewModel.statusMessageState().get());

        JsonNode root = new ObjectMapper().readTree(outputFile.toFile());
        // No copying for saveTo() - skinPath should point back at the original skin.json location, not a local "skin/" folder.
        assertTrue(root.get("skinPath").asText().endsWith("skin.json"));
        assertFalse(root.get("skinPath").asText().startsWith("skin/"));
        assertFalse(tempDir.resolve("skin").toFile().exists(), "saveTo() shouldn't copy the skin");
    }

    @Test
    void exportFailureIsReportedInsteadOfThrowing(@TempDir Path tempDir) {
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        // No skin loaded and no output dir writable assumption needed - currentSkin is null,
        // so SkinAssetExporter.copyInto(null, ...) throws NPE, which exportTo() must catch.
        viewModel.exportTo(tempDir.resolve("hud.json"));

        assertTrue(viewModel.statusMessageState().get().startsWith("Failed to export"),
                "unexpected status: " + viewModel.statusMessageState().get());
    }
}
