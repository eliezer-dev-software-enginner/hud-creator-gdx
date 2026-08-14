package my_app.project;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One placed widget as it appears in the exported JSON. {@code type} is one
 * of {@code "image"}, {@code "button"}, {@code "textButton"}, {@code "label"}
 * — matching the libGDX Scene2D class the widget maps to. {@code styleName}/
 * {@code regionName}/{@code text} are only meaningful for the types that use
 * them (mirroring {@link my_app.widget.WidgetSpec}'s variants); Jackson
 * omits whichever ones don't apply instead of writing them out as null.
 * {@code id} is the identifier a loader indexes actors by, so game code can
 * look one up and attach a real listener to it. {@code width}/{@code height}
 * (omitted when unset) are only written once the user actually resizes a
 * widget — absent means "size it from the skin's own preferred size."
 * {@code fontColor} ({@code #rrggbb}/{@code #rrggbbaa}) only applies to
 * {@code textButton}/{@code label}, and only when the user's actually
 * overridden it.
 * <p>
 * {@code anchorBaseId}/{@code anchorAlignX}/{@code anchorAlignY}/
 * {@code anchorOffsetX}/{@code anchorOffsetY} (all omitted when unset) let a
 * widget's position be resolved from the canvas or another widget's bounds
 * at load time instead of only ever being the fixed {@code x}/{@code y}
 * baked in at export — see {@link my_app.widget.PlacedWidget}'s javadoc for
 * the exact semantics. {@code x}/{@code y} are still always written even
 * when anchored, as a fallback for a loader that doesn't resolve anchors.
 * {@code groupId} (omitted when ungrouped) is an editor-only authoring
 * convenience (Ctrl+G) with no runtime meaning — a loader has no reason to
 * read it, it's here purely so groups survive a Save/Open round-trip.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlacedWidgetDto(
        String id,
        String type,
        String styleName,
        String regionName,
        String text,
        double x,
        double y,
        Double width,
        Double height,
        String fontColor,
        String anchorBaseId,
        String anchorAlignX,
        String anchorAlignY,
        Double anchorOffsetX,
        Double anchorOffsetY,
        String groupId
) {
}
