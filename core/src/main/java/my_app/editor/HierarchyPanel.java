package my_app.editor;

import com.badlogic.gdx.scenes.scene2d.Actor;
import imgui.ImGui;
import imgui.flag.ImGuiCond;

/**
 * Lists every placed widget as a selectable row, mirroring
 * scene-game-2d-editor's {@code HierarchyPanel} — a free function that
 * takes the current selection and returns the (possibly changed) one,
 * rather than owning selection state itself.
 * <p>
 * {@code dockX} comes from the caller (the canvas' own right edge, see
 * {@code HudEditorScreen.render}) rather than the window width — the
 * window is sized with a dedicated {@code INSPECTOR_WIDTH} column for
 * exactly this, so the panel never overlaps the canvas. An earlier version
 * anchored off {@code Gdx.graphics.getWidth()} instead, which (a) didn't
 * leave room for a column at all, since the canvas already spans most of
 * the window by design, and (b) only applied once via
 * {@code ImGuiCond.FirstUseEver} — resizing/maximizing the window
 * afterward didn't move it, so it drifted onto the canvas as soon as the
 * window grew relative to its startup size.
 */
public final class HierarchyPanel {

    private static final int PANEL_WIDTH = HudEditorScreen.INSPECTOR_WIDTH - 20;

    public Actor render(CanvasPanel canvas, Actor selected, float dockX) {
        ImGui.setNextWindowPos(dockX, 40, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(PANEL_WIDTH, 300, ImGuiCond.FirstUseEver);
        ImGui.begin("Hierarchy");

        Actor toDelete = null;
        for (Actor child : canvas.getChildren()) {
            if (!(child.getUserObject() instanceof PlacedMeta meta)) {
                continue; // background image / resize handle carry no PlacedMeta
            }
            boolean isSelected = child == selected;
            // "##" + a per-actor id keeps ImGui's own widget-id system unambiguous even if the
            // user renames two widgets to the same id — visible text stops at "##", so this
            // doesn't change what's shown, just what ImGui uses internally to tell rows apart.
            if (ImGui.selectable(meta.id + "##" + System.identityHashCode(child), isSelected)) {
                selected = child;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("Remove##" + System.identityHashCode(child))) {
                toDelete = child;
            }
        }

        if (toDelete != null) {
            toDelete.remove();
            if (selected == toDelete) {
                selected = null;
            }
        }

        ImGui.end();
        return selected;
    }
}
