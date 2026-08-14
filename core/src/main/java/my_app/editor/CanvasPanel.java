package my_app.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;

/**
 * The fixed-size target-resolution surface widgets get dropped onto —
 * everything else in the window (palette, chrome) lives outside it. Origin
 * is libGDX's own bottom-left/Y-up, same as every other {@link Group}; the
 * top-left/Y-down convention used by {@link my_app.widget.PlacedWidget} is
 * only a JSON/export concern, translated at the edges
 * ({@link HudEditorScreen#snapshotPlacedWidgets()} and
 * {@link HudEditorScreen#placeWidget}).
 * <p>
 * Children are clipped to these bounds (via {@link ScissorStack}) — a
 * widget dropped/resized/dragged past the edge (clamping elsewhere already
 * tries to prevent that, but the resize handle and the numeric Inspector
 * fields don't clamp as tightly) gets visually cut off at the canvas edge
 * rather than spilling into the palette, matching the old JavaFX-era app's
 * real clip on its own canvas.
 */
public final class CanvasPanel extends Group {

    private final Texture checkerTexture;
    private final TiledDrawable checker;
    private final Rectangle scissorBounds = new Rectangle();
    private final Rectangle clipBounds = new Rectangle();

    public CanvasPanel(int width, int height) {
        setSize(width, height);
        checkerTexture = buildCheckerTexture();
        checker = new TiledDrawable(new TextureRegion(checkerTexture));
    }

    private static Texture buildCheckerTexture() {
        int tile = 8;
        Pixmap pixmap = new Pixmap(tile * 2, tile * 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.valueOf("3a3a3aff"));
        pixmap.fill();
        pixmap.setColor(Color.valueOf("2e2e2eff"));
        pixmap.fillRectangle(0, 0, tile, tile);
        pixmap.fillRectangle(tile, tile, tile, tile);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        checker.draw(batch, getX(), getY(), getWidth(), getHeight());

        if (getStage() == null) {
            super.draw(batch, parentAlpha);
            return;
        }

        Vector2 stageOrigin = localToStageCoordinates(new Vector2(0, 0));
        clipBounds.set(stageOrigin.x, stageOrigin.y, getWidth(), getHeight());
        batch.flush();
        ScissorStack.calculateScissors(getStage().getCamera(), batch.getTransformMatrix(), clipBounds, scissorBounds);
        if (ScissorStack.pushScissors(scissorBounds)) {
            super.draw(batch, parentAlpha);
            batch.flush();
            ScissorStack.popScissors();
        } else {
            super.draw(batch, parentAlpha);
        }
    }

    public void dispose() {
        checkerTexture.dispose();
    }
}
