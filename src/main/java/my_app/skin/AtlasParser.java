package my_app.skin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a libGDX {@code TextureAtlas} text file ({@code .atlas}) — not JSON,
 * it's libGDX's own line-based format. The file is a sequence of blocks: a
 * name line (no colon) followed by indented {@code key: value} attribute
 * lines, until the next name line. The first block is always the atlas page
 * (the source image filename plus attributes like {@code size}/{@code format});
 * every block after that is a region.
 * <p>
 * Only single-page atlases are supported, which covers virtually every UI
 * skin atlas (multi-page atlases are for sprite sheets, not skins).
 */
public final class AtlasParser {

    private AtlasParser() {
    }

    public record AtlasFile(String pageImageFile, List<AtlasRegion> regions) {
    }

    public static AtlasFile parse(Path atlasFile) throws IOException {
        List<String> lines = Files.readAllLines(atlasFile);
        List<Block> blocks = parseBlocks(lines);

        if (blocks.isEmpty()) {
            throw new SkinLoadException("Empty atlas file: " + atlasFile);
        }

        Block page = blocks.get(0);
        List<AtlasRegion> regions = new ArrayList<>();
        for (int i = 1; i < blocks.size(); i++) {
            regions.add(toRegion(blocks.get(i), atlasFile));
        }

        return new AtlasFile(page.name(), regions);
    }

    private record Block(String name, Map<String, String> attrs) {
    }

    private static List<Block> parseBlocks(List<String> lines) {
        List<Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank()) {
                i++;
                continue;
            }

            String name = line.trim();
            i++;

            Map<String, String> attrs = new LinkedHashMap<>();
            while (i < lines.size()) {
                String next = lines.get(i);
                if (next.isBlank()) {
                    i++;
                    continue;
                }
                String trimmed = next.trim();
                int colon = trimmed.indexOf(':');
                if (colon < 0) break; // next block's name line

                attrs.put(trimmed.substring(0, colon).trim(), trimmed.substring(colon + 1).trim());
                i++;
            }

            blocks.add(new Block(name, attrs));
        }
        return blocks;
    }

    private static AtlasRegion toRegion(Block block, Path atlasFile) {
        Map<String, String> attrs = block.attrs();

        int[] xy = parseInts(attrs.get("xy"), block.name(), atlasFile);
        int[] size = parseInts(attrs.get("size"), block.name(), atlasFile);
        if (xy == null || size == null) {
            throw new SkinLoadException("Region \"" + block.name() + "\" in " + atlasFile
                    + " is missing xy/size");
        }

        boolean rotate = Boolean.parseBoolean(attrs.getOrDefault("rotate", "false"));
        int[] splits = parseInts(attrs.get("split"), block.name(), atlasFile);

        return new AtlasRegion(block.name(), xy[0], xy[1], size[0], size[1], rotate, splits);
    }

    private static int[] parseInts(String value, String regionName, Path atlasFile) {
        if (value == null) return null;
        String[] parts = value.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new SkinLoadException("Malformed number in region \"" + regionName + "\" ("
                        + atlasFile + "): \"" + value + "\"", e);
            }
        }
        return result;
    }
}
