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
    private final Map<String, TintedDrawableRef> tintedDrawables;

    public SkinModel(
            Path skinJsonPath,
            Path atlasImagePath,
            Map<String, AtlasRegion> regions,
            Map<String, SkinColor> colors,
            Map<String, String> fontFiles,
            Map<String, Map<String, Map<String, Object>>> styles,
            Map<String, TintedDrawableRef> tintedDrawables
    ) {
        this.skinJsonPath = skinJsonPath;
        this.atlasImagePath = atlasImagePath;
        this.regions = Map.copyOf(regions);
        this.colors = Map.copyOf(colors);
        this.fontFiles = Map.copyOf(fontFiles);
        this.styles = Map.copyOf(styles);
        this.tintedDrawables = Map.copyOf(tintedDrawables);
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

    /**
     * Resolves a drawable name the way libGDX's own {@code Skin.getDrawable}
     * does: either a plain atlas region directly, or — if {@code name} isn't
     * one — a {@code Skin.TintedDrawable} alias, in which case the result is
     * that alias's own base region plus the {@link SkinColor} it should be
     * tinted with. A style field (e.g. {@code ButtonStyle.up}) can point at
     * either kind, so callers resolving one shouldn't need to care which.
     */
    public Optional<ResolvedDrawable> drawable(String name) {
        AtlasRegion direct = regions.get(name);
        if (direct != null) {
            return Optional.of(new ResolvedDrawable(direct, null));
        }

        TintedDrawableRef tinted = tintedDrawables.get(name);
        if (tinted == null) {
            return Optional.empty();
        }
        AtlasRegion base = regions.get(tinted.regionName());
        if (base == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedDrawable(base, tinted.tint()));
    }

    /** {@code tint} is {@code null} for a plain region, non-null when {@code region} should be recolored (a {@code Skin.TintedDrawable} alias). */
    public record ResolvedDrawable(AtlasRegion region, SkinColor tint) {
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
