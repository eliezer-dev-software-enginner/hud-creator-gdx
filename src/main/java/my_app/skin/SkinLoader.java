package my_app.skin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads a full {@link SkinModel} from a {@code skin.json} path, following the
 * same convention libGDX's own {@code Skin(FileHandle)} constructor uses: the
 * atlas file sits next to the JSON with the same base name (e.g.
 * {@code skin.json} → {@code skin.atlas}), and the atlas page image is
 * resolved relative to the atlas file.
 */
public final class SkinLoader {

    private SkinLoader() {
    }

    public static SkinModel load(Path skinJsonPath) {
        Path dir = skinJsonPath.toAbsolutePath().normalize().getParent();
        String baseName = stripExtension(skinJsonPath.getFileName().toString());
        Path atlasPath = dir.resolve(baseName + ".atlas");

        if (!Files.isRegularFile(atlasPath)) {
            throw new SkinLoadException("Atlas file not found next to skin.json: " + atlasPath);
        }

        try {
            var parsedJson = SkinJsonParser.parse(skinJsonPath);
            var atlasFile = AtlasParser.parse(atlasPath);

            Path imagePath = dir.resolve(atlasFile.pageImageFile());
            if (!Files.isRegularFile(imagePath)) {
                throw new SkinLoadException("Atlas image not found: " + imagePath);
            }

            Map<String, AtlasRegion> regionsByName = new LinkedHashMap<>();
            for (AtlasRegion region : atlasFile.regions()) {
                regionsByName.put(region.name(), region);
            }

            return new SkinModel(
                    skinJsonPath,
                    imagePath,
                    regionsByName,
                    parsedJson.colors(),
                    parsedJson.fontFiles(),
                    parsedJson.styles()
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read skin at " + skinJsonPath, e);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
