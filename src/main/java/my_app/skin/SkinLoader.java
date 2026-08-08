package my_app.skin;

import my_app.gdx.GdxFontLoader;

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

            Map<String, GdxFontLoader.LoadedFont> fonts = new LinkedHashMap<>();
            for (var entry : parsedJson.fontFiles().entrySet()) {
                Path fntFile = dir.resolve(entry.getValue());
                String regionName = stripExtension(fntFile.getFileName().toString());
                try {
                    fonts.put(entry.getKey(), GdxFontLoader.load(atlasPath, fntFile, regionName));
                } catch (RuntimeException ignored) {
                    // Missing/unparseable .fnt, or its glyph region isn't in the atlas -
                    // BitmapTextView falls back to a plain rendering for this font,
                    // same graceful-degradation convention as every other lookup here.
                }
            }

            return new SkinModel(
                    skinJsonPath,
                    atlasPath,
                    imagePath,
                    regionsByName,
                    parsedJson.colors(),
                    parsedJson.fontFiles(),
                    parsedJson.styles(),
                    parsedJson.tintedDrawables(),
                    fonts
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
