package my_app.project;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The JSON layout written by "Save"/"Export" — everything a game loading
 * this screen needs to rebuild it with a real {@code Skin}: which skin to
 * load, the target viewport size, and where each widget goes.
 * <p>
 * Coordinates use the same convention as {@link my_app.widget.PlacedWidget}:
 * origin at the top-left, Y growing downward. libGDX's Scene2D uses the
 * opposite convention (origin bottom-left, Y growing upward) — a loader
 * needs to flip it: {@code stageY = canvasHeight - y - widgetHeight}.
 * <p>
 * Widget size isn't stored — a real Scene2D actor built with the same skin
 * (via {@code new Button(skin, styleName)}, etc.) sizes itself from the same
 * drawables/font this builder already used to preview it, so it doesn't need
 * to travel separately.
 * <p>
 * {@code backgroundImagePath} (omitted when unset) is a real, loadable
 * background — {@code scene2d-hud-loader} builds a Scene2D {@code Image}
 * from it, stretched to {@code canvasWidth}/{@code canvasHeight} and placed
 * behind every widget, the same way this builder's own canvas previews it.
 * Resolved the same way {@code skinPath} is: relative to wherever the layout
 * JSON itself ends up. "Export" copies the image file alongside the skin
 * so the export stays self-contained; "Save" references it in place.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiLayout(
        int formatVersion,
        String skinPath,
        int canvasWidth,
        int canvasHeight,
        String backgroundImagePath,
        List<PlacedWidgetDto> widgets
) {
}
