package my_app.widget;

/**
 * What to place on the Canva — either a raw atlas region (a decorative
 * image) or a widget resolved from a named style in the loaded skin.
 * {@link my_app.widget.render.WidgetViews} turns one of these into an
 * actual JavaFX preview; {@link PlacedWidget} pairs one with a canvas
 * position once it's been dropped.
 */
public sealed interface WidgetSpec {

    record ImageSpec(String regionName) implements WidgetSpec {
    }

    record ButtonSpec(String styleName) implements WidgetSpec {
    }

    /**
     * {@code fontColor} (a {@code #rrggbb}/{@code #rrggbbaa} hex string)
     * overrides the skin style's own font color when set — {@code null}
     * means "inherit from the skin," same as every widget before this
     * feature existed.
     */
    record TextButtonSpec(String styleName, String text, String fontColor) implements WidgetSpec {
        public TextButtonSpec(String styleName, String text) {
            this(styleName, text, null);
        }
    }

    /** @see TextButtonSpec */
    record LabelSpec(String styleName, String text, String fontColor) implements WidgetSpec {
        public LabelSpec(String styleName, String text) {
            this(styleName, text, null);
        }
    }
}
