package my_app.project;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One placed widget as it appears in the exported JSON. {@code type} is one
 * of {@code "image"}, {@code "button"}, {@code "textButton"}, {@code "label"}
 * — matching the libGDX Scene2D class the widget maps to. {@code styleName}/
 * {@code regionName}/{@code text} are only meaningful for the types that use
 * them (mirroring {@link my_app.widget.WidgetSpec}'s variants); Jackson
 * omits whichever ones don't apply instead of writing them out as null.
 * {@code nickname} (also omitted when unset) is an optional, user-assigned
 * label the libGDX-side loader indexes actors by, so game code can look one
 * up and attach a real listener to it.
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
        String nickname
) {
}
