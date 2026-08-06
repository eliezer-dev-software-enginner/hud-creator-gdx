package my_app.skin.render;

import javafx.scene.Node;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import megalodonte.base.components.Component;
import my_app.skin.AtlasRegion;
import my_app.skin.SkinColor;

/** Picks the right renderer for a region: {@link NinePatchView} if it has (non-rotated) split data, {@link AtlasImageView} otherwise. */
public final class DrawableView {

    private DrawableView() {
    }

    public static Component of(Image atlasImage, AtlasRegion region) {
        return of(atlasImage, region, null);
    }

    /** {@code tint}, when non-null, recolors the region — for a {@code Skin.TintedDrawable} alias (see {@link my_app.skin.SkinModel#drawable}), which reuses one base region for several colored variants instead of separate art per color. */
    public static Component of(Image atlasImage, AtlasRegion region, SkinColor tint) {
        return region.isNinePatch() && !region.rotate()
                ? new NinePatchView(atlasImage, region, tint)
                : new AtlasImageView(atlasImage, region, tint);
    }

    /**
     * Multiplies {@code target}'s own rendered colors by {@code tint} —
     * libGDX's own {@code TintedDrawable} sets the sprite batch's color
     * before drawing the (typically white/grayscale) base region, which is
     * exactly what {@code BlendMode.MULTIPLY} against a solid color layer
     * reproduces, preserving the region's own alpha/shading. The color
     * layer's size tracks {@code target}'s actual rendered bounds reactively
     * (not just at construction time) since both {@link AtlasImageView} and
     * {@link NinePatchView} can be resized after being built.
     */
    static void applyTint(Node target, SkinColor tint) {
        if (tint == null) return;

        Color fxColor = Color.color(tint.r(), tint.g(), tint.b(), tint.a());
        ColorInput colorLayer = new ColorInput(0, 0, 0, 0, fxColor);
        target.boundsInLocalProperty().addListener((obs, oldBounds, bounds) -> {
            colorLayer.setWidth(bounds.getWidth());
            colorLayer.setHeight(bounds.getHeight());
        });
        target.setEffect(new Blend(BlendMode.MULTIPLY, null, colorLayer));
    }
}
