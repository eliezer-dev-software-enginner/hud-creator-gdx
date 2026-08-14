package my_app.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.Align;
import my_app.widget.WidgetSpec;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds the draggable widget list from a loaded {@link Skin}: every atlas
 * region as an {@link WidgetSpec.ImageSpec}, every registered button/
 * text-button/label style as its matching spec, grouped under section
 * headers like the old JavaFX-era palette. Each row is wired as a
 * {@link DragAndDrop.Source} whose payload carries the {@link WidgetSpec}
 * itself and whose drag actor is a real preview built the same way the
 * dropped widget will be — no separate preview-rendering path to keep in
 * sync, unlike the JavaFX-era palette.
 */
public final class PaletteBuilder {

    private static final float THUMBNAIL_MAX = 40f;

    private PaletteBuilder() {
    }

    public static Table build(Skin skin, DragAndDrop dragAndDrop) {
        Table palette = new Table();
        palette.top().pad(6);

        addSectionHeader(palette, skin, "Regions");
        for (String regionName : regionNames(skin)) {
            addRow(palette, dragAndDrop, skin, new WidgetSpec.ImageSpec(regionName), regionName);
        }
        addSectionHeader(palette, skin, "Buttons");
        for (String styleName : skin.getAll(Button.ButtonStyle.class).keys()) {
            addRow(palette, dragAndDrop, skin, new WidgetSpec.ButtonSpec(styleName), styleName);
        }
        addSectionHeader(palette, skin, "Text buttons");
        for (String styleName : skin.getAll(TextButton.TextButtonStyle.class).keys()) {
            addRow(palette, dragAndDrop, skin, new WidgetSpec.TextButtonSpec(styleName, styleName), styleName);
        }
        addSectionHeader(palette, skin, "Labels");
        for (String styleName : skin.getAll(Label.LabelStyle.class).keys()) {
            addRow(palette, dragAndDrop, skin, new WidgetSpec.LabelSpec(styleName, styleName), styleName);
        }

        return palette;
    }

    /**
     * {@code title} renders with the loaded skin's own font, not this app's
     * — plain ASCII (English section names) is the safe choice here, since
     * an arbitrary game skin's font asset isn't guaranteed to include
     * glyphs beyond that (a "comic-ui"-style decorative font, for instance,
     * may only ship basic Latin letters). A missing glyph doesn't error, it
     * just silently drops the character, which is what motivated sticking
     * to ASCII here in the first place — this bit an earlier Portuguese
     * version of these same headers ("Botões" rendered as "Botes").
     */
    private static void addSectionHeader(Table palette, Skin skin, String title) {
        Label header = new Label(title, skin);
        header.setColor(Color.SKY);
        palette.add(header).left().padTop(10).padBottom(2).row();
    }

    /**
     * Atlas region names, excluding the raw glyph-sheet pages every
     * registered {@link BitmapFont} owns — those are packed into the same
     * atlas as everything else, but they're not meant to be placed as
     * standalone images (dragging one onto the canvas showed as a giant
     * sheet of jumbled characters). Detected generically via each font's
     * own {@code getRegions()} rather than by name pattern, so it works
     * regardless of how a given skin happens to name its font pages.
     */
    private static Set<String> regionNames(Skin skin) {
        Set<String> fontPageNames = new LinkedHashSet<>();
        for (BitmapFont font : skin.getAll(BitmapFont.class).values()) {
            for (TextureRegion page : font.getRegions()) {
                if (page instanceof TextureAtlas.AtlasRegion atlasRegion) {
                    fontPageNames.add(atlasRegion.name);
                }
            }
        }

        Set<String> names = new LinkedHashSet<>();
        for (TextureAtlas.AtlasRegion region : skin.getAtlas().getRegions()) {
            if (!fontPageNames.contains(region.name)) {
                names.add(region.name);
            }
        }
        return names;
    }

    private static void addRow(Table palette, DragAndDrop dragAndDrop, Skin skin, WidgetSpec spec, String caption) {
        Table row = new Table();
        row.pad(4);
        Actor preview = WidgetFactory.create(spec, skin);
        sizeToThumbnail(preview);
        row.add(preview).size(preview.getWidth(), preview.getHeight()).padRight(8);
        row.add(new Label(caption, skin)).left().expandX();

        palette.add(row).growX().left().row();

        dragAndDrop.addSource(new DragAndDrop.Source(row) {
            @Override
            public DragAndDrop.Payload dragStart(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer) {
                DragAndDrop.Payload payload = new DragAndDrop.Payload();
                payload.setObject(spec);
                // Uncapped, unlike the palette row's thumbnail — this previews the widget at the size it'll actually be dropped at.
                Actor dragPreview = WidgetFactory.create(spec, skin);
                sizeToPref(dragPreview);
                payload.setDragActor(dragPreview);
                payload.setInvalidDragActor(dragPreview);
                payload.setValidDragActor(dragPreview);
                return payload;
            }
        });
    }

    static void sizeToPref(Actor actor) {
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout layout) {
            actor.setSize(layout.getPrefWidth(), layout.getPrefHeight());
        }
        // getStage() is null until added — Align only needed once positioned, safe to skip here.
        if (actor instanceof Label label) {
            label.setAlignment(Align.center);
        }
    }

    /**
     * Same as {@link #sizeToPref}, but clamped so an oversized preview
     * doesn't blow out the palette row. Two different kinds of "oversized"
     * need two different fixes: a large decorative *image* just needs its
     * bounds shrunk (that alone shrinks how it renders); a
     * {@link Label}/{@link TextButton} whose skin style has a large
     * decorative *font* does not — {@code Actor.setSize(...)} only changes
     * layout bounds, it does not rescale glyphs, so a style literally named
     * "big" or "title" would still draw at full native size regardless of
     * a shrunk bounding box, bleeding into whichever row is next. Only
     * {@code setFontScale(...)} actually shrinks the rendered text, so that
     * has to happen — and be re-measured — before the generic bounds clamp.
     */
    private static void sizeToThumbnail(Actor actor) {
        sizeToPref(actor);
        shrinkFontIfOversized(actor);
        sizeToPref(actor); // re-measure — prefWidth/Height now reflect the (possibly) smaller glyphs

        float w = actor.getWidth(), h = actor.getHeight();
        float scale = Math.min(1f, THUMBNAIL_MAX / Math.max(w, h));
        if (scale < 1f) {
            actor.setSize(w * scale, h * scale);
        }
    }

    private static void shrinkFontIfOversized(Actor actor) {
        Label label = actor instanceof Label l ? l
                : actor instanceof TextButton textButton ? textButton.getLabel()
                : null;
        if (label != null && label.getPrefHeight() > THUMBNAIL_MAX) {
            label.setFontScale(THUMBNAIL_MAX / label.getPrefHeight());
        }
    }
}
