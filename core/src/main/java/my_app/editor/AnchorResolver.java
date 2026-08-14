package my_app.editor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import my_app.widget.PlacedWidget;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves anchored widgets' positions from their base (the canvas, or
 * another widget by id) + alignment + offset — mirrors
 * scene-game-2d-editor's own {@code AnchorResolver}, and
 * {@code scene2d-hud-loader}'s copy for the runtime side, with one
 * deliberate difference from both: this is never baked into a plain fixed
 * x/y — see {@link PlacedWidget}'s javadoc for why (device screen sizes
 * vary at runtime, a fixed pixel position doesn't, so the resolution has to
 * happen wherever the layout is actually loaded, not once at export time).
 * <p>
 * Works entirely in this app's own top-left/Y-down coordinate convention
 * (matching {@link PlacedWidget}/the exported JSON), not libGDX's native
 * bottom-left/Y-up — flips only once, right when calling
 * {@code Actor#setPosition}, same as everywhere else in this app.
 * <p>
 * Runs every frame ({@code HudEditorScreen#render()}, right after
 * {@code stage.act()}) so dragging a base widget, resizing it, or resizing
 * the canvas keeps every anchored dependent in sync live. Dangling
 * references, self-anchors, and cycles all degrade gracefully: the actor
 * just keeps whatever position it already has instead of resolving further.
 */
final class AnchorResolver {

    private AnchorResolver() {
    }

    /** {@code actorsById} must include every actor carrying a {@link PlacedMeta} — needed so a widget can anchor to another widget's *current* size, not just its id. */
    static void resolve(CanvasPanel canvas, Map<String, Actor> actorsById) {
        Set<Actor> resolved = new HashSet<>();
        Set<Actor> visiting = new HashSet<>();
        for (Actor actor : actorsById.values()) {
            resolve(actor, canvas, actorsById, resolved, visiting);
        }
    }

    private static void resolve(Actor actor, CanvasPanel canvas, Map<String, Actor> actorsById, Set<Actor> resolved, Set<Actor> visiting) {
        if (resolved.contains(actor)) return;
        if (!(actor.getUserObject() instanceof PlacedMeta meta) || meta.anchorBaseId == null) {
            resolved.add(actor);
            return;
        }

        float baseX, baseTopY, baseWidth, baseHeight;
        if (PlacedWidget.CANVAS_ANCHOR.equals(meta.anchorBaseId)) {
            baseX = 0;
            baseTopY = 0;
            baseWidth = canvas.getWidth();
            baseHeight = canvas.getHeight();
        } else {
            Actor base = actorsById.get(meta.anchorBaseId);
            if (base == null || base == actor || visiting.contains(base)) {
                resolved.add(actor); // dangling reference, self-anchor, or cycle — leave wherever it already is
                return;
            }
            visiting.add(actor);
            resolve(base, canvas, actorsById, resolved, visiting);
            visiting.remove(actor);
            baseX = base.getX();
            baseTopY = canvas.getHeight() - base.getY() - base.getHeight(); // Scene2D Y-up -> this app's Y-down
            baseWidth = base.getWidth();
            baseHeight = base.getHeight();
        }

        float objectWidth = actor.getWidth();
        float objectHeight = actor.getHeight();
        float alignedX = switch (meta.anchorAlignX) {
            case "right" -> baseX + baseWidth - objectWidth;
            case "center" -> baseX + baseWidth / 2f - objectWidth / 2f;
            default -> baseX; // "left"
        };
        float alignedTopY = switch (meta.anchorAlignY) {
            case "bottom" -> baseTopY + baseHeight - objectHeight;
            case "center" -> baseTopY + baseHeight / 2f - objectHeight / 2f;
            default -> baseTopY; // "top"
        };
        float dataX = alignedX + (float) meta.anchorOffsetX;
        float dataTopY = alignedTopY + (float) meta.anchorOffsetY;

        actor.setPosition(dataX, canvas.getHeight() - dataTopY - objectHeight); // back to Scene2D Y-up
        resolved.add(actor);
    }
}
