package my_app.skin.render;

import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderImage;
import javafx.scene.layout.BorderRepeat;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.Region;
import megalodonte.base.components.Component;
import my_app.skin.AtlasRegion;

/**
 * Renders a 9-patch {@link AtlasRegion} (one with {@code split} data) using
 * JavaFX's border-image machinery: corners stay pixel-perfect, edges stretch
 * along one axis, and the center fills the rest — the same behavior as
 * libGDX's own {@code NinePatch} drawable.
 */
public class NinePatchView extends Component {
    private final Region region;

    public NinePatchView(Image atlasImage, AtlasRegion atlasRegion) {
        super(new Region());
        this.region = (Region) this.node;

        if (!atlasRegion.isNinePatch()) {
            throw new IllegalArgumentException("Region \"" + atlasRegion.name() + "\" has no split data");
        }
        if (atlasRegion.rotate()) {
            // Packers generally disable rotation for split regions since it
            // would scramble the split metadata, so this combination is not
            // expected in practice.
            throw new IllegalArgumentException(
                    "Rotated 9-patch regions aren't supported (\"" + atlasRegion.name() + "\")");
        }

        Image cropped = crop(atlasImage, atlasRegion);

        // libGDX split order is [left, right, top, bottom]; BorderWidths wants (top, right, bottom, left).
        int[] splits = atlasRegion.splits();
        BorderWidths sliceWidths = new BorderWidths(splits[2], splits[1], splits[3], splits[0]);

        BorderImage borderImage = new BorderImage(
                cropped,
                sliceWidths,
                Insets.EMPTY,
                sliceWidths,
                true,
                BorderRepeat.STRETCH,
                BorderRepeat.STRETCH
        );

        region.setBackground(Background.EMPTY);
        region.setBorder(new Border(borderImage));
        region.setPrefSize(atlasRegion.width(), atlasRegion.height());
    }

    /** Resizes the rendered patch — corners/edges keep their native pixel size, only the center stretches. */
    public NinePatchView size(double width, double height) {
        region.setPrefSize(width, height);
        return this;
    }

    private static Image crop(Image atlasImage, AtlasRegion r) {
        PixelReader reader = atlasImage.getPixelReader();
        return new WritableImage(reader, r.x(), r.y(), r.width(), r.height());
    }
}
