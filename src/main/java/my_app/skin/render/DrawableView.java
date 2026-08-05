package my_app.skin.render;

import javafx.scene.image.Image;
import megalodonte.base.components.Component;
import my_app.skin.AtlasRegion;

/** Picks the right renderer for a region: {@link NinePatchView} if it has (non-rotated) split data, {@link AtlasImageView} otherwise. */
public final class DrawableView {

    private DrawableView() {
    }

    public static Component of(Image atlasImage, AtlasRegion region) {
        return region.isNinePatch() && !region.rotate()
                ? new NinePatchView(atlasImage, region)
                : new AtlasImageView(atlasImage, region);
    }
}
