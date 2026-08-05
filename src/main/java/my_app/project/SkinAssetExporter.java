package my_app.project;

import my_app.skin.SkinModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Copies a loaded skin's files (skin.json, its .atlas, the atlas's page
 * image, and every referenced BitmapFont file) next to an exported UI
 * layout, so the export is self-contained and immediately usable in the
 * target libGDX project instead of leaving the user to go copy the skin
 * over by hand.
 */
public final class SkinAssetExporter {

    private SkinAssetExporter() {
    }

    /** Copies the skin into {@code targetDir}, returning the copied skin.json's file name (for {@link UiLayout#skinPath()}). */
    public static String copyInto(SkinModel skin, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        Path skinJson = skin.skinJsonPath();
        Path skinDir = skinJson.toAbsolutePath().normalize().getParent();
        String baseName = stripExtension(skinJson.getFileName().toString());
        Path atlasFile = skinDir.resolve(baseName + ".atlas");

        copy(skinJson, targetDir);
        copy(atlasFile, targetDir);
        copy(skin.atlasImagePath(), targetDir);

        for (String fontName : skin.fontNames()) {
            Optional<String> fontFileName = skin.fontFile(fontName);
            if (fontFileName.isPresent()) {
                copy(skinDir.resolve(fontFileName.get()), targetDir);
            }
        }

        return skinJson.getFileName().toString();
    }

    private static void copy(Path source, Path targetDir) throws IOException {
        Files.copy(source, targetDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
