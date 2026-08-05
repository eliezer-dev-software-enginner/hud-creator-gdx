package my_app.skin;

/**
 * One named region inside a libGDX {@code TextureAtlas} page (a rectangle of
 * pixels in {@code skin.png}). {@code splits} is {@code null} for a plain
 * region; when present it's the libGDX 9-patch split order
 * {@code [left, right, top, bottom]}, taken straight from the atlas file's
 * {@code split:} line.
 */
public record AtlasRegion(String name, int x, int y, int width, int height, boolean rotate, int[] splits) {

    public boolean isNinePatch() {
        return splits != null;
    }
}
