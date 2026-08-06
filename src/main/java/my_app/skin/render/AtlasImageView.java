package my_app.skin.render;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import megalodonte.base.components.Component;
import my_app.skin.AtlasRegion;
import my_app.skin.SkinColor;

/** Renders a plain (non 9-patch) {@link AtlasRegion} by cropping it out of the shared atlas page image. */
public class AtlasImageView extends Component {
    private final ImageView imageView;

    public AtlasImageView(Image atlasImage, AtlasRegion atlasRegion) {
        this(atlasImage, atlasRegion, null);
    }

    /** {@code tint} is non-null when {@code atlasRegion} is a {@code Skin.TintedDrawable} alias's base region. */
    public AtlasImageView(Image atlasImage, AtlasRegion atlasRegion, SkinColor tint) {
        super(new ImageView(atlasImage));
        this.imageView = (ImageView) this.node;

        imageView.setViewport(new Rectangle2D(
                atlasRegion.x(), atlasRegion.y(), atlasRegion.width(), atlasRegion.height()));
        imageView.setPreserveRatio(false);
        imageView.setSmooth(false);

        if (atlasRegion.rotate()) {
            // Packers rotate irregularly-shaped regions 90° to pack tighter. None
            // of the regions in our example skins use this, so it's untested —
            // revisit if a rotated region ever shows up distorted.
            imageView.setRotate(-90);
        }

        DrawableView.applyTint(imageView, tint);
    }

    public ImageView imageView() {
        return imageView;
    }
}
