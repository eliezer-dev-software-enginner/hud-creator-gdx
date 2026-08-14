package my_app.project;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Copies a loaded skin's files (skin.json, its .atlas, the atlas's page
 * image(s), and every referenced BitmapFont file) next to an exported UI
 * layout, so the export is self-contained and immediately usable in the
 * target libGDX project instead of leaving the user to go copy the skin
 * over by hand.
 * <p>
 * Unlike the JavaFX-era version of this class (which had to parse skin.json
 * by hand because there was no GL context to build a real {@code Skin}),
 * this only needs headless-safe libGDX classes — {@link JsonReader} and
 * {@link TextureAtlas.TextureAtlasData} both just read files, they don't
 * touch GL — so copying works whether or not a canvas window happens to be
 * open.
 */
public final class SkinAssetExporter {

    private SkinAssetExporter() {
    }

    /** Copies the skin into {@code targetDir}, returning the copied skin.json's file name (for {@link UiLayout#skinPath()}). */
    public static String copyInto(Path skinJson, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        Path skinDir = skinJson.toAbsolutePath().normalize().getParent();
        String baseName = stripExtension(skinJson.getFileName().toString());
        Path atlasFile = skinDir.resolve(baseName + ".atlas");

        copy(skinJson, targetDir);
        copy(atlasFile, targetDir);

        TextureAtlas.TextureAtlasData atlasData = new TextureAtlas.TextureAtlasData(
                new FileHandle(atlasFile.toFile()), new FileHandle(skinDir.toFile()), false);
        for (TextureAtlas.TextureAtlasData.Page page : atlasData.getPages()) {
            copy(page.textureFile.file().toPath(), targetDir);
        }

        for (String fontFileName : fontFileNames(skinJson)) {
            copy(skinDir.resolve(fontFileName), targetDir);
        }

        return skinJson.getFileName().toString();
    }

    /** Scans skin.json for every {@code BitmapFont} style's {@code "file"} entry. */
    private static Set<String> fontFileNames(Path skinJson) throws IOException {
        JsonValue root = new JsonReader().parse(new FileHandle(skinJson.toFile()));
        Set<String> fontFiles = new LinkedHashSet<>();
        JsonValue fontsByStyleName = root.get("com.badlogic.gdx.graphics.g2d.BitmapFont");
        if (fontsByStyleName != null) {
            for (JsonValue style = fontsByStyleName.child; style != null; style = style.next) {
                String file = style.getString("file", null);
                if (file != null) {
                    fontFiles.add(file);
                }
            }
        }
        return fontFiles;
    }

    private static void copy(Path source, Path targetDir) throws IOException {
        Files.copy(source, targetDir.resolve(source.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
