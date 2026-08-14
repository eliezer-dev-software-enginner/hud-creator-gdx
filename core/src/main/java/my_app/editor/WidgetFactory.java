package my_app.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import my_app.widget.WidgetSpec;

/**
 * Turns a {@link WidgetSpec} into a real Scene2D actor built from the
 * loaded {@link Skin} — no reimplementation of libGDX's own drawable/font
 * rendering needed, unlike the JavaFX-era builder, because this runs inside
 * an actual GL context.
 */
public final class WidgetFactory {

    private WidgetFactory() {
    }

    public static Actor create(WidgetSpec spec, Skin skin) {
        return switch (spec) {
            case WidgetSpec.ImageSpec s -> new Image(skin.getAtlas().findRegion(s.regionName()));
            case WidgetSpec.ButtonSpec s -> new Button(skin, s.styleName());
            case WidgetSpec.TextButtonSpec s -> textButton(s, skin);
            case WidgetSpec.LabelSpec s -> label(s, skin);
        };
    }

    private static TextButton textButton(WidgetSpec.TextButtonSpec s, Skin skin) {
        TextButton.TextButtonStyle style = skin.get(s.styleName(), TextButton.TextButtonStyle.class);
        if (s.fontColor() != null) {
            style = new TextButton.TextButtonStyle(style);
            style.fontColor = Color.valueOf(s.fontColor());
        }
        return new TextButton(s.text(), style);
    }

    private static Label label(WidgetSpec.LabelSpec s, Skin skin) {
        Label.LabelStyle style = skin.get(s.styleName(), Label.LabelStyle.class);
        if (s.fontColor() != null) {
            style = new Label.LabelStyle(style);
            style.fontColor = Color.valueOf(s.fontColor());
        }
        return new Label(s.text(), style);
    }

    /**
     * How small {@code actor} can be resized before its own content stops
     * fitting — {@code Actor.setSize(...)} stretches a widget's background
     * but never rescales {@link Label}/{@link TextButton} glyphs, so
     * shrinking either below its own {@code getPrefWidth()} (which already
     * accounts for the button style's padding, not just the raw text)
     * makes the text overflow the widget's bounds. Anything without text
     * has no such floor and falls back to {@code fallback}.
     */
    public static float minWidth(Actor actor, float fallback) {
        if (actor instanceof Label label) return label.getPrefWidth();
        if (actor instanceof TextButton textButton) return textButton.getPrefWidth();
        return fallback;
    }

    /** @see #minWidth */
    public static float minHeight(Actor actor, float fallback) {
        if (actor instanceof Label label) return label.getPrefHeight();
        if (actor instanceof TextButton textButton) return textButton.getPrefHeight();
        return fallback;
    }
}
