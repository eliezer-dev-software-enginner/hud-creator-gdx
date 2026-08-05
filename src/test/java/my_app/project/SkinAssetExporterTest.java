package my_app.project;

import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinAssetExporterTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @Test
    void copiesSkinJsonAtlasImageAndFonts(@TempDir Path tempDir) throws Exception {
        SkinModel skin = SkinLoader.load(EXAMPLE_SKIN);
        Path targetDir = tempDir.resolve("skin");

        String skinFileName = SkinAssetExporter.copyInto(skin, targetDir);

        assertEquals("skin.json", skinFileName);
        assertTrue(targetDir.resolve("skin.json").toFile().isFile());
        assertTrue(targetDir.resolve("skin.atlas").toFile().isFile());
        assertTrue(targetDir.resolve("skin.png").toFile().isFile());
        for (String fontName : skin.fontNames()) {
            String fontFile = skin.fontFile(fontName).orElseThrow();
            assertTrue(targetDir.resolve(fontFile).toFile().isFile(), fontFile + " should have been copied");
        }
    }
}
