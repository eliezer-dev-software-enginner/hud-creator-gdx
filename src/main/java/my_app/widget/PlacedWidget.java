package my_app.widget;

/**
 * A widget the user dropped onto the Canva, tracked for bookkeeping and JSON
 * export. Position is top-left, matching {@code Canva.child(component, x, y)}'s
 * own contract. {@code nickname} is an optional, user-assigned label (e.g.
 * "jogar") distinct from {@code id} (auto-generated, e.g. "widget-3") — the
 * libGDX-side loader looks actors up by nickname so game code can attach real
 * listeners to them after loading. {@code width}/{@code height} are {@code null}
 * until the user drags the resize handle — meaning "still the skin's own
 * preferred size," not "zero."
 */
public record PlacedWidget(String id, WidgetSpec spec, double x, double y, String nickname, Double width, Double height) {
}
