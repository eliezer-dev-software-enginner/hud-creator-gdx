package my_app.skin;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A fully loaded libGDX skin: the atlas regions, colors, font file references,
 * and style definitions from {@code skin.json}, resolved against a single
 * atlas image. Built by {@link SkinLoader}.
 */
public final class SkinModel {

    private final Path skinJsonPath;
    private final Path atlasImagePath;
    private final Map<String, AtlasRegion> regions;
    private final Map<String, SkinColor> colors;
    private final Map<String, String> fontFiles;
    private final Map<String, Map<String, Map<String, Object>>> styles;

    public SkinModel(
            Path skinJsonPath,
            Path atlasImagePath,
            Map<String, AtlasRegion> regions,
            Map<String, SkinColor> colors,
            Map<String, String> fontFiles,
            Map<String, Map<String, Map<String, Object>>> styles
    ) {
        this.skinJsonPath = skinJsonPath;
        this.atlasImagePath = atlasImagePath;
        this.regions = Map.copyOf(regions);
        this.colors = Map.copyOf(colors);
        this.fontFiles = Map.copyOf(fontFiles);
        this.styles = Map.copyOf(styles);
    }

    public Path skinJsonPath() {
        return skinJsonPath;
    }

    public Path atlasImagePath() {
        return atlasImagePath;
    }

    public Optional<AtlasRegion> region(String name) {
        return Optional.ofNullable(regions.get(name));
    }

    public Collection<AtlasRegion> regions() {
        return regions.values();
    }

    public Optional<SkinColor> color(String name) {
        return Optional.ofNullable(colors.get(name));
    }

    public Optional<String> fontFile(String name) {
        return Optional.ofNullable(fontFiles.get(name));
    }

    /** Names of every {@code BitmapFont} declared in the skin, e.g. {@code "font"}. */
    public Set<String> fontNames() {
        return fontFiles.keySet();
    }

    /** Style class simple names present in this skin, e.g. {@code "ButtonStyle"}, {@code "LabelStyle"}. */
    public Set<String> styleClasses() {
        return styles.keySet();
    }

    /** Named style instances for a given style class, e.g. {@code "default"}, {@code "toggle"} for {@code "ButtonStyle"}. */
    public Set<String> styleNames(String styleClass) {
        return styles.getOrDefault(styleClass, Map.of()).keySet();
    }

    /** Raw field values (region names, color names, numbers) for one named style. */
    public Optional<Map<String, Object>> style(String styleClass, String styleName) {
        return Optional.ofNullable(styles.getOrDefault(styleClass, Map.of()).get(styleName));
    }
}
