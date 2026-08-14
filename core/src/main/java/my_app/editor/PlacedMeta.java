package my_app.editor;

import my_app.widget.WidgetSpec;

/**
 * Bookkeeping attached to a placed actor via {@code Actor#setUserObject}.
 * Position isn't duplicated here — it's read live from the actor's own
 * x/y whenever a {@link my_app.widget.PlacedWidget} snapshot is needed (see
 * {@link HudEditorScreen#snapshotPlacedWidgets()}), so dragging an actor
 * around the canvas never needs to keep a second copy in sync. {@code id}
 * and {@code spec} are both mutable (not final): {@code id} is editable via
 * {@link InspectorPanel} (it's the only user-facing identifier — there's no
 * separate nickname), and "editing" a text-button/label's text means
 * swapping {@code spec} for a new instance with the same style but
 * different text, since the record itself is immutable.
 * <p>
 * {@code anchorBaseId} (null = free positioning, otherwise
 * {@link my_app.widget.PlacedWidget#CANVAS_ANCHOR} or another widget's id)
 * and the alignment/offset fields below it are the *only* anchor state kept
 * here — unlike x/y, these can't be read back from the actor, so they do
 * live here. When set, {@link AnchorResolver} owns the actor's position
 * every frame; {@link HudEditorScreen#makeMovable} skips dragging such an
 * actor directly (see its own comment) — reposition it via the Inspector's
 * offset fields instead.
 * <p>
 * {@code groupId} (null = ungrouped) is set by Ctrl+G on the current
 * multi-selection and cleared by Ctrl+Shift+G — see
 * {@link my_app.widget.PlacedWidget}'s javadoc for the exact semantics
 * (selection/Inspector stay per-widget; drag/delete/copy/duplicate expand
 * to the whole group).
 */
public final class PlacedMeta {
    public String id;
    public WidgetSpec spec;
    public Double width;
    public Double height;
    public String anchorBaseId;
    public String anchorAlignX = "left";
    public String anchorAlignY = "top";
    public double anchorOffsetX;
    public double anchorOffsetY;
    public String groupId;

    public PlacedMeta(String id, WidgetSpec spec, Double width, Double height) {
        this.id = id;
        this.spec = spec;
        this.width = width;
        this.height = height;
    }
}
