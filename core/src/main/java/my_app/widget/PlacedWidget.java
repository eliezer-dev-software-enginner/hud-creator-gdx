package my_app.widget;

/**
 * A widget the user dropped onto the canvas, tracked for bookkeeping and JSON
 * export. Position is top-left, Y growing downward — the Stage-side actor
 * flips this to libGDX's own bottom-left/Y-up convention when placed
 * ({@code stageY = canvasHeight - y - height}). {@code id} is the single
 * identifier a game loading this layout looks widgets up by — user-editable
 * via the Inspector, defaults to an auto-generated "widget-N" when placed.
 * {@code width}/{@code height} are {@code null} until the user drags the
 * resize handle — meaning "still the skin's own preferred size," not "zero."
 * <p>
 * {@code x}/{@code y} are always present, even for an anchored widget — a
 * loader that doesn't understand the anchor fields below still gets a
 * reasonable (if not responsive) position, since they're kept as the last
 * value the anchor resolved to at export time. {@code anchorBaseId} (null =
 * free positioning) is either {@link #CANVAS_ANCHOR} or another widget's
 * {@code id}; {@code anchorAlignX} is {@code "left"/"center"/"right"},
 * {@code anchorAlignY} is {@code "top"/"center"/"bottom"} (both matching this
 * record's own top-left/Y-down convention, not libGDX's), and
 * {@code anchorOffsetX}/{@code anchorOffsetY} are added to the aligned
 * position afterward (positive X moves right, positive Y moves down,
 * regardless of alignment side — so a right- or bottom-anchored widget wants
 * a *negative* offset for an inward margin). Resolving these into a real
 * position — using whatever canvas size and base-widget sizes are actually
 * live at load time, not baked at export time — is what makes a layout
 * survive different device dimensions; see {@code AnchorResolver} on both
 * the editor and {@code scene2d-hud-loader} sides.
 * <p>
 * {@code groupId} (null = ungrouped) is purely an editor/authoring
 * convenience — every widget sharing the same id was grouped together via
 * Ctrl+G, so dragging, deleting, copying, or duplicating any one of them
 * (when it's the sole selection) acts on the whole group at once. Clicking
 * to select and editing in the Inspector still target only the one widget
 * actually clicked, deliberately — grouping doesn't change that. Round-trips
 * through Save/Open so groups survive reopening a layout, but
 * {@code scene2d-hud-loader} has no reason to care about it at all — it has
 * no runtime meaning, unlike the anchor fields above.
 */
public record PlacedWidget(
        String id,
        WidgetSpec spec,
        double x,
        double y,
        Double width,
        Double height,
        String anchorBaseId,
        String anchorAlignX,
        String anchorAlignY,
        Double anchorOffsetX,
        Double anchorOffsetY,
        String groupId
) {
    /** {@code anchorBaseId} sentinel meaning "anchor to the canvas' own 0,0..canvasWidth,canvasHeight", not another widget. */
    public static final String CANVAS_ANCHOR = "__canvas__";
}
