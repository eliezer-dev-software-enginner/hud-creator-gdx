package my_app.skin;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real skins (gdx-skins "flat-earth", Skin Composer exports) commonly declare
 * {@code Skin.TintedDrawable} aliases — a named variant that reuses another
 * region's art, recolored — instead of separate atlas art per color state.
 * Before {@link SkinModel#drawable} existed, style fields pointing at one of
 * these aliases (e.g. {@code ButtonStyle.up = "button-close-c"}) failed with
 * "Region not found", since only direct regions were resolvable.
 */
class SkinModelTest {

    @Test
    void resolvesTintedDrawableAliasToItsBaseRegionAndColor() {
        AtlasRegion baseRegion = new AtlasRegion("button-close", 0, 0, 10, 10, false, null);
        SkinColor tintColor = new SkinColor(1, 0, 0, 1);

        SkinModel skin = new SkinModel(
                Path.of("skin.json"),
                Path.of("skin.png"),
                Map.of("button-close", baseRegion),
                Map.of("color", tintColor),
                Map.of(),
                Map.of(),
                Map.of("button-close-c", new TintedDrawableRef("button-close", tintColor))
        );

        var resolved = skin.drawable("button-close-c").orElseThrow();
        assertEquals(baseRegion, resolved.region());
        assertEquals(tintColor, resolved.tint());
    }

    @Test
    void resolvesAPlainRegionWithNoTint() {
        AtlasRegion baseRegion = new AtlasRegion("button-close", 0, 0, 10, 10, false, null);

        SkinModel skin = new SkinModel(
                Path.of("skin.json"),
                Path.of("skin.png"),
                Map.of("button-close", baseRegion),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        var resolved = skin.drawable("button-close").orElseThrow();
        assertEquals(baseRegion, resolved.region());
        assertEquals(null, resolved.tint());
    }

    @Test
    void anUnknownNameResolvesToNothing() {
        SkinModel skin = new SkinModel(
                Path.of("skin.json"), Path.of("skin.png"), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

        assertFalse(skin.drawable("nonexistent").isPresent());
    }

    @Test
    void aTintedDrawableAliasWithNoMatchingRegionResolvesToNothing() {
        SkinModel skin = new SkinModel(
                Path.of("skin.json"), Path.of("skin.png"), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("button-close-c", new TintedDrawableRef("button-close", new SkinColor(1, 0, 0, 1))));

        assertFalse(skin.drawable("button-close-c").isPresent());
    }
}
