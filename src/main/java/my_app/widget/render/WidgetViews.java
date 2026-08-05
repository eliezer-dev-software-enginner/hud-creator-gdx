package my_app.widget.render;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import megalodonte.base.components.Component;
import my_app.skin.AtlasRegion;
import my_app.skin.SkinColor;
import my_app.skin.SkinModel;
import my_app.skin.render.DrawableView;
import my_app.widget.WidgetSpec;

import java.util.Map;

/**
 * Resolves a {@link WidgetSpec} against a loaded {@link SkinModel} into an
 * actual preview {@link Component} — the same code path used for both the
 * palette's catalog entries and whatever gets placed on the Canva, so
 * dragging a preview onto the canvas never looks different from the preview
 * itself.
 * <p>
 * Text is rendered with a plain JavaFX {@link Text} (system font), not the
 * skin's actual BitmapFont glyphs — {@link SkinModel#fontFile} only exposes
 * the {@code .fnt} path, and parsing/rendering real bitmap font glyphs is
 * follow-up work, not needed to preview layout/color/position.
 */
public final class WidgetViews {

    private WidgetViews() {
    }

    public static Component build(SkinModel skin, Image atlasImage, WidgetSpec spec) {
        return switch (spec) {
            case WidgetSpec.ImageSpec s -> buildImage(skin, atlasImage, s);
            case WidgetSpec.ButtonSpec s -> buildButton(skin, atlasImage, s);
            case WidgetSpec.TextButtonSpec s -> buildTextButton(skin, atlasImage, s);
            case WidgetSpec.LabelSpec s -> buildLabel(skin, s);
        };
    }

    private static Component buildImage(SkinModel skin, Image atlasImage, WidgetSpec.ImageSpec spec) {
        AtlasRegion region = skin.region(spec.regionName())
                .orElseThrow(() -> new IllegalArgumentException("Unknown region: " + spec.regionName()));
        return DrawableView.of(atlasImage, region);
    }

    private static Component buildButton(SkinModel skin, Image atlasImage, WidgetSpec.ButtonSpec spec) {
        AtlasRegion region = resolveRegion(skin, "ButtonStyle", spec.styleName(), "up");
        return DrawableView.of(atlasImage, region);
    }

    private static Component buildTextButton(SkinModel skin, Image atlasImage, WidgetSpec.TextButtonSpec spec) {
        AtlasRegion region = resolveRegion(skin, "TextButtonStyle", spec.styleName(), "up");
        Component background = DrawableView.of(atlasImage, region);

        Text label = new Text(spec.text());
        label.setFill(resolveFontColor(skin, "TextButtonStyle", spec.styleName()));
        label.setMouseTransparent(true);

        StackPane stack = new StackPane(background.getNode(), label);
        StackPane.setAlignment(label, Pos.CENTER);
        return Component.CreateFromJavaFxNode(stack);
    }

    private static Component buildLabel(SkinModel skin, WidgetSpec.LabelSpec spec) {
        Text label = new Text(spec.text());
        label.setFill(resolveFontColor(skin, "LabelStyle", spec.styleName()));
        return Component.CreateFromJavaFxNode(label);
    }

    private static AtlasRegion resolveRegion(SkinModel skin, String styleClass, String styleName, String field) {
        Map<String, Object> style = requireStyle(skin, styleClass, styleName);
        Object value = style.get(field);
        if (!(value instanceof String regionName)) {
            throw new IllegalArgumentException(styleClass + "." + styleName + " has no \"" + field + "\" drawable");
        }
        return skin.region(regionName)
                .orElseThrow(() -> new IllegalArgumentException("Region not found: " + regionName));
    }

    private static Color resolveFontColor(SkinModel skin, String styleClass, String styleName) {
        Object value = requireStyle(skin, styleClass, styleName).get("fontColor");
        if (!(value instanceof String colorName)) return Color.BLACK;

        SkinColor color = skin.color(colorName).orElse(null);
        return color == null ? Color.BLACK : Color.color(color.r(), color.g(), color.b(), color.a());
    }

    private static Map<String, Object> requireStyle(SkinModel skin, String styleClass, String styleName) {
        return skin.style(styleClass, styleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown " + styleClass + "." + styleName));
    }
}
