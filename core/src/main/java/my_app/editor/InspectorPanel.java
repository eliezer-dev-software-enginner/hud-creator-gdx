package my_app.editor;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Edits the selected widget in place, mirroring scene-game-2d-editor's
 * {@code InspectorPanel}: reusable {@code ImString}/{@code ImFloat} buffer
 * fields (not recreated per frame — ImGui needs a stable native buffer),
 * pushed from the model each frame and written back immediately whenever
 * {@code ImGui.inputXxx(...)} reports an edit. Per-field editing only makes
 * sense for exactly one selected widget — a multi-selection just shows a
 * count, matching the old JavaFX-era app's inspector.
 * <p>
 * Position/size use the same top-left/Y-down convention as
 * {@link my_app.widget.PlacedWidget} — not libGDX's own bottom-left/Y-up —
 * so the numbers shown here match what ends up in the exported JSON.
 * <p>
 * Docked below {@link HierarchyPanel}, both at the same {@code dockX}
 * (the canvas' own right edge, passed in by the caller) — see that class'
 * javadoc for why that's not the window width.
 */
public final class InspectorPanel {

    private static final int PANEL_WIDTH = HudEditorScreen.INSPECTOR_WIDTH - 20;
    private static final String[] ALIGN_X_KEYS = {"left", "center", "right"};
    private static final String[] ALIGN_X_LABELS = {"Left", "Center", "Right"};
    private static final String[] ALIGN_Y_KEYS = {"top", "center", "bottom"};
    private static final String[] ALIGN_Y_LABELS = {"Top", "Center", "Bottom"};

    private final ImString idField = new ImString(64);
    private final ImString textField = new ImString(128);
    private final ImString fontColorField = new ImString(16);
    private final ImFloat xField = new ImFloat();
    private final ImFloat yField = new ImFloat();
    private final ImFloat widthField = new ImFloat();
    private final ImFloat heightField = new ImFloat();
    private final ImInt anchorBaseIndex = new ImInt();
    private final ImInt anchorAlignXIndex = new ImInt();
    private final ImInt anchorAlignYIndex = new ImInt();
    private final ImFloat anchorOffsetXField = new ImFloat();
    private final ImFloat anchorOffsetYField = new ImFloat();

    public void render(Set<Actor> selectedActors, CanvasPanel canvas, Skin skin, float dockX) {
        ImGui.setNextWindowPos(dockX, 350, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(PANEL_WIDTH, 300, ImGuiCond.FirstUseEver);
        ImGui.begin("Inspector");

        if (selectedActors.isEmpty()) {
            ImGui.text("No widget selected.");
        } else if (selectedActors.size() > 1) {
            ImGui.text(selectedActors.size() + " widgets selected.");
        } else {
            renderSingle(selectedActors.iterator().next(), canvas, skin);
        }

        ImGui.end();
    }

    private void renderSingle(Actor selected, CanvasPanel canvas, Skin skin) {
        if (!(selected.getUserObject() instanceof PlacedMeta meta)) {
            ImGui.text("No widget selected.");
            return;
        }

        ImGui.text("Type: " + typeLabel(meta.spec));

        // The only identifier — blank input is ignored rather than accepted, since an empty id
        // isn't meaningful; duplicate ids across widgets aren't blocked (matches how the rest of
        // this tool stays permissive — e.g. an invalid font-color hex below is also just ignored
        // rather than rejected with an error), but see HierarchyPanel for why that's still safe.
        idField.set(meta.id);
        if (ImGui.inputText("Id", idField)) {
            String newId = idField.get().trim();
            if (!newId.isEmpty()) {
                meta.id = newId;
            }
        }

        renderTextFieldIfApplicable(selected, meta);
        renderFontColorFieldIfApplicable(selected, meta, skin);
        renderTransformFields(selected, meta, canvas.getHeight());
        renderAnchorFields(selected, meta, canvas);
    }

    private static String typeLabel(WidgetSpec spec) {
        return switch (spec) {
            case WidgetSpec.ImageSpec s -> "Image (" + s.regionName() + ")";
            case WidgetSpec.ButtonSpec s -> "Button (" + s.styleName() + ")";
            case WidgetSpec.TextButtonSpec s -> "Text button (" + s.styleName() + ")";
            case WidgetSpec.LabelSpec s -> "Label (" + s.styleName() + ")";
        };
    }

    private void renderTextFieldIfApplicable(Actor actor, PlacedMeta meta) {
        String currentText = switch (meta.spec) {
            case WidgetSpec.TextButtonSpec s -> s.text();
            case WidgetSpec.LabelSpec s -> s.text();
            default -> null;
        };
        if (currentText == null) {
            return;
        }

        textField.set(currentText);
        if (!ImGui.inputText("Text", textField)) {
            return;
        }
        String newText = textField.get();
        if (actor instanceof TextButton textButton && meta.spec instanceof WidgetSpec.TextButtonSpec s) {
            textButton.setText(newText);
            meta.spec = new WidgetSpec.TextButtonSpec(s.styleName(), newText, s.fontColor());
        } else if (actor instanceof Label label && meta.spec instanceof WidgetSpec.LabelSpec s) {
            label.setText(newText);
            meta.spec = new WidgetSpec.LabelSpec(s.styleName(), newText, s.fontColor());
        }
    }

    /**
     * Blank reverts to the skin style's own color; an invalid hex is
     * silently ignored (same behavior as the old app) — the field still
     * shows whatever was typed, it just doesn't get applied until it
     * parses. Changing the color means rebuilding a copy of the base skin
     * style, same as {@link WidgetFactory} does when first placing the
     * widget — {@code setStyle(...)} on the live actor picks it up
     * immediately without recreating the actor.
     */
    private void renderFontColorFieldIfApplicable(Actor actor, PlacedMeta meta, Skin skin) {
        String currentColor = switch (meta.spec) {
            case WidgetSpec.TextButtonSpec s -> s.fontColor();
            case WidgetSpec.LabelSpec s -> s.fontColor();
            default -> null;
        };
        boolean applicable = meta.spec instanceof WidgetSpec.TextButtonSpec || meta.spec instanceof WidgetSpec.LabelSpec;
        if (!applicable) {
            return;
        }

        fontColorField.set(currentColor == null ? "" : currentColor);
        if (!ImGui.inputText("Font color (#rrggbb)", fontColorField)) {
            return;
        }
        String hex = fontColorField.get().trim();
        String newColor = hex.isEmpty() ? null : hex;

        if (actor instanceof TextButton textButton && meta.spec instanceof WidgetSpec.TextButtonSpec s) {
            Color parsed = parseColorOrNull(newColor);
            if (newColor == null || parsed != null) {
                TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(skin.get(s.styleName(), TextButton.TextButtonStyle.class));
                if (parsed != null) style.fontColor = parsed;
                textButton.setStyle(style);
                meta.spec = new WidgetSpec.TextButtonSpec(s.styleName(), s.text(), newColor);
            }
        } else if (actor instanceof Label label && meta.spec instanceof WidgetSpec.LabelSpec s) {
            Color parsed = parseColorOrNull(newColor);
            if (newColor == null || parsed != null) {
                Label.LabelStyle style = new Label.LabelStyle(skin.get(s.styleName(), Label.LabelStyle.class));
                if (parsed != null) style.fontColor = parsed;
                label.setStyle(style);
                meta.spec = new WidgetSpec.LabelSpec(s.styleName(), s.text(), newColor);
            }
        }
    }

    private static Color parseColorOrNull(String hex) {
        if (hex == null) return null;
        try {
            return Color.valueOf(hex);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void renderTransformFields(Actor selected, PlacedMeta meta, float canvasHeight) {
        float topLeftY = canvasHeight - selected.getY() - selected.getHeight();

        // Anchored: X/Y are AnchorResolver's output, not something typing a number here should
        // fight with every frame — shown read-only, edit the anchor's offset fields instead.
        if (meta.anchorBaseId != null) {
            ImGui.text(String.format("X: %.1f  Y: %.1f (resolved by anchor)", selected.getX(), topLeftY));
        } else {
            xField.set(selected.getX());
            if (ImGui.inputFloat("X", xField)) {
                selected.setX(xField.get());
            }

            yField.set(topLeftY);
            if (ImGui.inputFloat("Y", yField)) {
                selected.setY(canvasHeight - yField.get() - selected.getHeight());
            }
        }

        // Clamped, not just rejected below zero — same content-driven floor the resize handle
        // uses (a Label/TextButton's text doesn't rescale with setSize(), so shrinking past what
        // it needs makes it overflow the widget; see WidgetFactory.minWidth/minHeight).
        widthField.set(selected.getWidth());
        if (ImGui.inputFloat("Width", widthField)) {
            float newWidth = Math.max(WidgetFactory.minWidth(selected, 1f), widthField.get());
            resizeKeepingTopLeft(selected, newWidth, selected.getHeight());
            meta.width = (double) newWidth;
        }

        heightField.set(selected.getHeight());
        if (ImGui.inputFloat("Height", heightField)) {
            float newHeight = Math.max(WidgetFactory.minHeight(selected, 1f), heightField.get());
            resizeKeepingTopLeft(selected, selected.getWidth(), newHeight);
            meta.height = (double) newHeight;
        }
    }

    /** Same invariant the resize handle keeps: the top-left corner doesn't move when width/height changes. */
    private static void resizeKeepingTopLeft(Actor actor, float newWidth, float newHeight) {
        float top = actor.getY() + actor.getHeight();
        actor.setSize(newWidth, newHeight);
        actor.setY(top - newHeight);
    }

    /**
     * "Base" is either none (free positioning, the default), the canvas
     * itself, or another placed widget's id — {@link AnchorResolver} turns
     * this into a real position every frame, using whatever canvas/base size
     * is actually live, which is what makes it survive a canvas resize (and,
     * for {@code scene2d-hud-loader}'s own copy of this resolver, a
     * different device's screen size) instead of only ever being the exact
     * pixel position it had when set.
     */
    private void renderAnchorFields(Actor selected, PlacedMeta meta, CanvasPanel canvas) {
        ImGui.separator();
        ImGui.text("Anchor");

        List<String> otherIds = new ArrayList<>();
        for (Actor child : canvas.getChildren()) {
            if (child != selected && child.getUserObject() instanceof PlacedMeta otherMeta) {
                otherIds.add(otherMeta.id);
            }
        }
        String[] options = new String[otherIds.size() + 2];
        options[0] = "None (free positioning)";
        options[1] = "Canvas";
        for (int i = 0; i < otherIds.size(); i++) {
            options[i + 2] = otherIds.get(i);
        }

        int currentIndex;
        if (meta.anchorBaseId == null) {
            currentIndex = 0;
        } else if (PlacedWidget.CANVAS_ANCHOR.equals(meta.anchorBaseId)) {
            currentIndex = 1;
        } else {
            int idx = otherIds.indexOf(meta.anchorBaseId);
            // A dangling reference (renamed/deleted base) shows as "None" here without actually
            // clearing meta.anchorBaseId — AnchorResolver already handles that case gracefully
            // (leaves the widget wherever it last was), and picking any option here is a
            // deliberate user action via the combo's own return value, not this display step.
            currentIndex = idx >= 0 ? idx + 2 : 0;
        }
        anchorBaseIndex.set(currentIndex);
        if (ImGui.combo("Base", anchorBaseIndex, options)) {
            int picked = anchorBaseIndex.get();
            meta.anchorBaseId = switch (picked) {
                case 0 -> null;
                case 1 -> PlacedWidget.CANVAS_ANCHOR;
                default -> otherIds.get(picked - 2);
            };
        }

        if (meta.anchorBaseId == null) {
            return;
        }

        anchorAlignXIndex.set(indexOf(ALIGN_X_KEYS, meta.anchorAlignX));
        if (ImGui.combo("Align X", anchorAlignXIndex, ALIGN_X_LABELS)) {
            meta.anchorAlignX = ALIGN_X_KEYS[anchorAlignXIndex.get()];
        }

        anchorAlignYIndex.set(indexOf(ALIGN_Y_KEYS, meta.anchorAlignY));
        if (ImGui.combo("Align Y", anchorAlignYIndex, ALIGN_Y_LABELS)) {
            meta.anchorAlignY = ALIGN_Y_KEYS[anchorAlignYIndex.get()];
        }

        // Added *after* alignment, same direction regardless of side (positive X right, positive
        // Y down) — for a right- or bottom-anchored widget, a negative offset is the inward
        // margin, matching scene-game-2d-editor's own AnchorResolver's offset convention.
        anchorOffsetXField.set((float) meta.anchorOffsetX);
        if (ImGui.inputFloat("Offset X", anchorOffsetXField)) {
            meta.anchorOffsetX = anchorOffsetXField.get();
        }

        anchorOffsetYField.set((float) meta.anchorOffsetY);
        if (ImGui.inputFloat("Offset Y", anchorOffsetYField)) {
            meta.anchorOffsetY = anchorOffsetYField.get();
        }
    }

    private static int indexOf(String[] keys, String value) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equals(value)) return i;
        }
        return 0;
    }
}
