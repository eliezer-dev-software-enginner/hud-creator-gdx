package my_app.widget.render;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import megalodonte.base.components.Component;
import my_app.gdx.GdxFontLoader;
import my_app.skin.SkinColor;
import my_app.skin.SkinModel;
import my_app.skin.render.BitmapTextView;
import my_app.skin.render.DrawableView;
import my_app.widget.WidgetSpec;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves a {@link WidgetSpec} against a loaded {@link SkinModel} into an
 * actual preview {@link Component} — the same code path used for both the
 * palette's catalog entries and whatever gets placed on the Canva, so
 * dragging a preview onto the canvas never looks different from the preview
 * itself.
 * <p>
 * Text is rendered with a {@link BitmapTextView} — the real BitmapFont
 * glyphs straight from the skin's own atlas, pixel-for-pixel the same as the
 * real libGDX game, not a substitute JavaFX system font.
 */
public final class WidgetViews {

    private WidgetViews() {
    }

    public static Component build(SkinModel skin, Image atlasImage, WidgetSpec spec) {
        return switch (spec) {
            case WidgetSpec.ImageSpec s -> buildImage(skin, atlasImage, s);
            case WidgetSpec.ButtonSpec s -> buildButton(skin, atlasImage, s);
            case WidgetSpec.TextButtonSpec s -> buildTextButton(skin, atlasImage, s);
            case WidgetSpec.LabelSpec s -> buildLabel(skin, atlasImage, s);
        };
    }

    private static Component buildImage(SkinModel skin, Image atlasImage, WidgetSpec.ImageSpec spec) {
        SkinModel.ResolvedDrawable drawable = skin.drawable(spec.regionName())
                .orElseThrow(() -> new IllegalArgumentException("Unknown region: " + spec.regionName()));
        return DrawableView.of(atlasImage, drawable.region(), drawable.tint());
    }

    private static Component buildButton(SkinModel skin, Image atlasImage, WidgetSpec.ButtonSpec spec) {
        SkinModel.ResolvedDrawable drawable = resolveDrawable(skin, "ButtonStyle", spec.styleName(), "up");
        return DrawableView.of(atlasImage, drawable.region(), drawable.tint());
    }

    private static Component buildTextButton(SkinModel skin, Image atlasImage, WidgetSpec.TextButtonSpec spec) {
        SkinModel.ResolvedDrawable drawable = resolveDrawable(skin, "TextButtonStyle", spec.styleName(), "up");
        Component background = DrawableView.of(atlasImage, drawable.region(), drawable.tint());

        Color fontColor = resolveFontColorOverride(skin, "TextButtonStyle", spec.styleName(), spec.fontColor());
        Optional<GdxFontLoader.LoadedFont> font = resolveFont(skin, "TextButtonStyle", spec.styleName());
        BitmapTextView label = new BitmapTextView(atlasImage, font, spec.text(), fontColor);
        label.getNode().setMouseTransparent(true);

        StackPane stack = new StackPane(background.getNode(), label.getNode());
        StackPane.setAlignment(label.getNode(), Pos.CENTER);
        return Component.CreateFromJavaFxNode(stack);
    }

    private static Component buildLabel(SkinModel skin, Image atlasImage, WidgetSpec.LabelSpec spec) {
        Color fontColor = resolveFontColorOverride(skin, "LabelStyle", spec.styleName(), spec.fontColor());
        Optional<GdxFontLoader.LoadedFont> font = resolveFont(skin, "LabelStyle", spec.styleName());
        BitmapTextView label = new BitmapTextView(atlasImage, font, spec.text(), fontColor);
        label.getNode().setMouseTransparent(true);

        // Wrapped in a StackPane (no background child - LabelStyle has none)
        // so a Label has the same kind of resizable container TextButton/Button
        // do: the box CanvasController's resize handle drags is well-defined,
        // even though - matching real Scene2D Label.setSize() with wrap off -
        // the glyphs themselves don't reflow to fit it.
        StackPane stack = new StackPane(label.getNode());
        StackPane.setAlignment(label.getNode(), Pos.CENTER);
        return Component.CreateFromJavaFxNode(stack);
    }

    private static SkinModel.ResolvedDrawable resolveDrawable(SkinModel skin, String styleClass, String styleName, String field) {
        Map<String, Object> style = requireStyle(skin, styleClass, styleName);
        Object value = style.get(field);
        if (!(value instanceof String regionName)) {
            throw new IllegalArgumentException(styleClass + "." + styleName + " has no \"" + field + "\" drawable");
        }
        return skin.drawable(regionName)
                .orElseThrow(() -> new IllegalArgumentException("Region not found: " + regionName));
    }

    /**
     * The skin style's own font color for a {@code TextButtonSpec}/{@code LabelSpec}
     * — i.e. what {@link #build} would use with no {@code fontColor} override
     * at all. Used by {@link my_app.CanvasController#setFontColor} to revert
     * a widget back to "inherit from the skin" (clearing the override rather
     * than hardcoding whatever color happened to be showing).
     */
    public static Color skinFontColor(WidgetSpec spec, SkinModel skin) {
        return switch (spec) {
            case WidgetSpec.TextButtonSpec s -> resolveFontColor(skin, "TextButtonStyle", s.styleName());
            case WidgetSpec.LabelSpec s -> resolveFontColor(skin, "LabelStyle", s.styleName());
            default -> Color.BLACK;
        };
    }

    private static Color resolveFontColor(SkinModel skin, String styleClass, String styleName) {
        Object value = requireStyle(skin, styleClass, styleName).get("fontColor");
        if (!(value instanceof String colorName)) return Color.BLACK;

        SkinColor color = skin.color(colorName).orElse(null);
        return color == null ? Color.BLACK : Color.color(color.r(), color.g(), color.b(), color.a());
    }

    /**
     * A {@code TextButtonSpec}/{@code LabelSpec}'s own {@code fontColor}
     * override on top of the skin style's own color — {@code null} means
     * "keep the skin's own value" (only ever set by
     * {@link my_app.CanvasController#setFontColor} on an existing widget,
     * once the user actually overrides it). An invalid hex color is ignored
     * (keeps the skin's color) rather than throwing — the "Properties" panel
     * is expected to validate before calling this far, but this is the last
     * line of defense against a corrupt/hand-edited layout JSON.
     */
    private static Color resolveFontColorOverride(SkinModel skin, String styleClass, String styleName, String fontColorOverride) {
        if (fontColorOverride != null) {
            try {
                return Color.web(fontColorOverride);
            } catch (IllegalArgumentException ignored) {
                // invalid hex - fall through to the skin's own color
            }
        }
        return resolveFontColor(skin, styleClass, styleName);
    }

    /** The real BitmapFont behind a style's own {@code font} field, resolved against the skin - empty if the style has none, or it didn't load (see {@link SkinModel#font}). */
    private static Optional<GdxFontLoader.LoadedFont> resolveFont(SkinModel skin, String styleClass, String styleName) {
        Object value = requireStyle(skin, styleClass, styleName).get("font");
        if (!(value instanceof String fontName)) return Optional.empty();
        return skin.font(fontName);
    }

    private static Map<String, Object> requireStyle(SkinModel skin, String styleClass, String styleName) {
        return skin.style(styleClass, styleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + styleClass + "." + styleName));
    }

    /**
     * The editable {@link BitmapTextView} inside a node {@link #build}
     * returned for a {@code TextButtonSpec}/{@code LabelSpec}, or
     * {@code null} for the other kinds. Both are a {@code StackPane} with
     * the label's {@code Canvas} as its last child ({@code buildTextButton}'s
     * has a background before it; {@code buildLabel}'s has only the label) -
     * read from the end rather than a fixed index so either shape resolves
     * the same way. The scene graph only ever holds the raw {@code Canvas},
     * not this wrapper, so recovering it goes through {@link BitmapTextView#of}.
     */
    public static BitmapTextView bitmapTextViewOf(Node builtNode) {
        if (builtNode instanceof StackPane stack && !stack.getChildren().isEmpty()) {
            return BitmapTextView.of(stack.getChildren().get(stack.getChildren().size() - 1));
        }
        return null;
    }

    /**
     * Resizes a node {@link #build} returned — the resize handle's write
     * path (both live while dragging, and once when restoring a previously
     * resized {@link my_app.widget.PlacedWidget}). Every kind {@link #build}
     * returns is either an {@link ImageView} directly ({@code Image}/{@code Button}),
     * or a {@link StackPane} whose first child is the actual visual drawable
     * ({@code TextButton}'s background; {@code Label} has none). The
     * {@code StackPane} itself is resized too so {@code CanvasController}'s
     * own bounds math ({@code node.prefWidth(-1)}) sees the new size — using
     * {@code setPrefSize} (not {@code resize}), the same as {@link my_app.skin.render.NinePatchView#size},
     * so a later layout pass can't silently reset it back to a stale computed
     * preferred size.
     */
    public static void resize(Node builtNode, double width, double height) {
        if (builtNode instanceof StackPane stack) {
            lockSize(stack, width, height);
            if (!stack.getChildren().isEmpty()) {
                resizeVisual(stack.getChildren().get(0), width, height);
            }
        } else {
            resizeVisual(builtNode, width, height);
        }
    }

    private static void resizeVisual(Node node, double width, double height) {
        if (node instanceof ImageView imageView) {
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
        } else if (node instanceof Region region) {
            lockSize(region, width, height);
        }
        // A bare Canvas (Label's own glyphs) is left alone - it doesn't reflow, only its wrapper does.
    }

    private static void lockSize(Region region, double width, double height) {
        region.setPrefSize(width, height);
        region.setMinSize(width, height);
        region.setMaxSize(width, height);
    }
}
