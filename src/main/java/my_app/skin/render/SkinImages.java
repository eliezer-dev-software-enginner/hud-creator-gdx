package my_app.skin.render;

import javafx.scene.image.Image;
import my_app.skin.SkinModel;

/** Loads the shared atlas page image for a skin — reuse one instance across every region view. */
public final class SkinImages {

    private SkinImages() {
    }

    public static Image loadAtlasImage(SkinModel skin) {
        return new Image(skin.atlasImagePath().toUri().toString());
    }
}
