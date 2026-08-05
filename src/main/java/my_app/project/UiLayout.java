package my_app.project;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The JSON layout written by "Save"/"Export" — everything a future
 * libGDX-side parser (Fase 6) needs to rebuild this screen with a real
 * {@code Skin}: which skin to load, the target viewport size, and where each
 * widget goes.
 * <p>
 * Coordinates use the same convention as {@link my_app.widget.PlacedWidget}
 * and the Canva itself: origin at the top-left, Y growing downward, {@code x}/
 * {@code y} is each widget's top-left corner. libGDX's Scene2D uses the
 * opposite Y convention (origin bottom-left, Y growing upward) — Fase 6 needs
 * to flip it: {@code stageY = canvasHeight - y - widgetHeight}.
 * <p>
 * Widget size isn't stored — a real Scene2D actor built with the same skin
 * (via {@code new Button(skin, styleName)}, etc.) sizes itself from the same
 * drawables/font this builder already used to preview it, so it doesn't need
 * to travel separately.
 * <p>
 * {@code backgroundImagePath} (omitted when unset) is purely a builder
 * convenience — a reference image shown behind the Canva so "Load
 * Layout" restores it too. Real games draw backgrounds with
 * {@code SpriteBatch}, not the Scene2D UI this builder produces, so a
 * libGDX-side parser is expected to ignore this field entirely.
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
