package my_app;

import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import megalodonte.base.components.Component;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinModel;
import my_app.skin.render.SkinImages;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import my_app.widget.render.WidgetViews;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places {@link WidgetSpec}s onto the Canva — either one at a time, centered
 * on a drop point ({@link #place}), or a whole saved layout at their exact
 * saved positions ({@link #loadLayout}) — records a matching
 * {@link PlacedWidget} in {@link HomeScreenViewModel#placedWidgets()} for
 * each, and makes every placed node draggable within the canvas afterward
 * (plain mouse-press/drag, updating {@code layoutX}/{@code layoutY} directly
 * — separate from the Dragboard-based drag used to drop a *new* widget in
 * from the palette) plus selectable — pressing a widget sets
 * {@link HomeScreenViewModel#selectedWidgetIdState()}, which highlights it
 * here and drives the "Properties" panel (nickname editing) in
 * {@link SkinPalettePanel} — shows center-alignment guides while dragging
 * (see {@link #applyCenterGuide}), and draws a grid overlay reacting to
 * {@link HomeScreenViewModel#showingGridState()}.
 * <p>
 * {@link #place} is deliberately not tied to any actual {@code DragEvent} —
 * the real drag-and-drop handler calls it with the drop coordinates, and so
 * can a test, without needing to simulate a real OS-level drag gesture.
 */
final class CanvasController {

    private static final DropShadow SELECTION_HIGHLIGHT = new DropShadow(8, Themes.SELECTION_HIGHLIGHT_COLOR);
    private static final Color GUIDE_COLOR = Themes.GUIDE_COLOR;
    private static final double GUIDE_SHOW_DISTANCE = 15;
    private static final double GUIDE_SNAP_DISTANCE = 5;
    private static final double GUIDE_WIDTH_NEAR = 1;
    private static final double GUIDE_WIDTH_SNAPPED = 2.5;
    private static final double GRID_SPACING = 20;
    private static final Color GRID_COLOR = Themes.GRID_COLOR;

    private final Canva canva;
    private final HomeScreenViewModel viewModel;
    private final Map<String, Node> nodesById = new HashMap<>();
    private final Line verticalCenterGuide = new Line();
    private final Line horizontalCenterGuide = new Line();
    private final Canvas gridCanvas = new Canvas();

    CanvasController(Canva canva, HomeScreenViewModel viewModel) {
        this.canva = canva;
        this.viewModel = viewModel;
        viewModel.selectedWidgetIdState().subscribe(this::applySelectionHighlight);
        setUpGrid();
        setUpGuides();
        setUpDeleteKey();
    }

    /**
     * Deletes the selected widget on Delete/Backspace. Keyboard events only
     * reach a node that has focus, and the Canva's Pane isn't focus-traversable
     * or focused by default (it's a plain Pane, not a Control) — so widget
     * selection ({@link #makeMovable}'s press handler) also requests focus on
     * the pane, otherwise this would never fire from a real key press.
     */
    private void setUpDeleteKey() {
        Pane pane = (Pane) canva.getNode();
        pane.setFocusTraversable(true);
        pane.setOnKeyPressed(event -> {
            if (event.getCode() != KeyCode.DELETE && event.getCode() != KeyCode.BACK_SPACE) return;

            String selectedId = viewModel.selectedWidgetIdState().get();
            if (selectedId != null) {
                removeWidget(selectedId);
                event.consume();
            }
        });
    }

    private void setUpGrid() {
        gridCanvas.setMouseTransparent(true);
        ((Pane) canva.getNode()).getChildren().add(gridCanvas); // added first - behind widgets and guides

        Runnable redraw = this::redrawGrid;
        viewModel.showingGridState().subscribe(showing -> redraw.run());
        viewModel.canvasWidthState().subscribe(w -> redraw.run());
        viewModel.canvasHeightState().subscribe(h -> redraw.run());
    }

    private void redrawGrid() {
        double width = canvasWidth();
        double height = canvasHeight();
        gridCanvas.setWidth(width);
        gridCanvas.setHeight(height);

        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, width, height);
        if (!viewModel.showingGridState().get()) return;

        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);
        for (double x = 0; x <= width; x += GRID_SPACING) {
            gc.strokeLine(x, 0, x, height);
        }
        for (double y = 0; y <= height; y += GRID_SPACING) {
            gc.strokeLine(0, y, width, y);
        }
    }

    private void setUpGuides() {
        for (Line guide : List.of(verticalCenterGuide, horizontalCenterGuide)) {
            guide.setStroke(GUIDE_COLOR);
            guide.setMouseTransparent(true);
            guide.setVisible(false);
            ((Pane) canva.getNode()).getChildren().add(guide);
        }
    }

    void place(WidgetSpec spec, double centerX, double centerY) {
        SkinModel skin = viewModel.skinState().get();
        if (skin == null) return;

        Image atlasImage = SkinImages.loadAtlasImage(skin);
        Component widget = WidgetViews.build(skin, atlasImage, spec);
        Node widgetNode = widget.getNode();

        double width = widgetNode.prefWidth(-1);
        double height = widgetNode.prefHeight(-1);
        double x = clamp(centerX - width / 2, 0, Math.max(0, canvasWidth() - width));
        double y = clamp(centerY - height / 2, 0, Math.max(0, canvasHeight() - height));

        finishPlacing(widget, spec, x, y, viewModel.nextWidgetId(), null);
    }

    /** Clears the canvas and re-places every widget from {@code widgets} at its saved (x, y) — used by "Load Layout". */
    void loadLayout(List<PlacedWidget> widgets) {
        clear();

        SkinModel skin = viewModel.skinState().get();
        if (skin == null) return;
        Image atlasImage = SkinImages.loadAtlasImage(skin);

        for (PlacedWidget widget : widgets) {
            Component component = WidgetViews.build(skin, atlasImage, widget.spec());
            finishPlacing(component, widget.spec(), widget.x(), widget.y(), widget.id(), widget.nickname());
        }
    }

    /** Removes every widget currently on the canvas (the grid and the two guide lines aren't widgets, and stay). */
    void clear() {
        ((Pane) canva.getNode()).getChildren().removeIf(
                node -> node != gridCanvas && node != verticalCenterGuide && node != horizontalCenterGuide);
        viewModel.placedWidgets().clear();
        nodesById.clear();
        viewModel.selectedWidgetIdState().set(null);
    }

    /** Sets or clears a widget's nickname — called live as the user types in the "Properties" panel, so no status-bar spam per keystroke. */
    void setNickname(String widgetId, String nicknameOrNull) {
        viewModel.placedWidgets().updateIf(
                w -> w.id().equals(widgetId),
                w -> new PlacedWidget(w.id(), w.spec(), w.x(), w.y(), nicknameOrNull));
    }

    /** Removes one widget from the canvas — wired to Delete/Backspace in {@link #setUpDeleteKey}, but plain enough to call directly (tests, future "Remove" menu item, etc). */
    void removeWidget(String widgetId) {
        Node node = nodesById.remove(widgetId);
        if (node == null) return;

        ((Pane) canva.getNode()).getChildren().remove(node);
        viewModel.placedWidgets().removeIf(w -> w.id().equals(widgetId));

        if (widgetId.equals(viewModel.selectedWidgetIdState().get())) {
            viewModel.selectedWidgetIdState().set(null);
        }
    }

    /** The JavaFX node for a placed widget id, or null — used by tests instead of indexing into the Canva's Pane children (which also holds the guide lines). */
    Node nodeFor(String widgetId) {
        return nodesById.get(widgetId);
    }

    Line verticalCenterGuide() {
        return verticalCenterGuide;
    }

    Line horizontalCenterGuide() {
        return horizontalCenterGuide;
    }

    Canvas gridCanvas() {
        return gridCanvas;
    }

    private void finishPlacing(Component component, WidgetSpec spec, double x, double y, String id, String nickname) {
        Node node = component.getNode();
        canva.child(component, x, y);
        nodesById.put(id, node);
        viewModel.placedWidgets().add(new PlacedWidget(id, spec, x, y, nickname));
        makeMovable(node, id);
    }

    /** Lets an already-placed {@code node} be selected (click/press) and dragged around the canvas by mouse, clamped to stay fully inside it and snapping to the canvas's center guides. */
    private void makeMovable(Node node, String widgetId) {
        node.setCursor(Cursor.MOVE);

        // Offset between the mouse-press point and the node's layoutX/Y, in
        // scene coordinates - scene and pane-local space differ by a fixed
        // translation here (no scale/rotate anywhere in this UI), so plain
        // scene-coordinate deltas already equal pane-local deltas.
        double[] pressOffset = new double[2];

        node.setOnMousePressed(event -> {
            viewModel.selectedWidgetIdState().set(widgetId);
            ((Pane) canva.getNode()).requestFocus(); // so the Delete/Backspace handler in setUpDeleteKey actually receives key events
            pressOffset[0] = event.getSceneX() - node.getLayoutX();
            pressOffset[1] = event.getSceneY() - node.getLayoutY();
            event.consume();
        });

        node.setOnMouseDragged(event -> {
            double width = node.prefWidth(-1);
            double height = node.prefHeight(-1);

            double newX = clamp(event.getSceneX() - pressOffset[0], 0, Math.max(0, canvasWidth() - width));
            double newY = clamp(event.getSceneY() - pressOffset[1], 0, Math.max(0, canvasHeight() - height));

            newX = applyCenterGuide(newX, width, canvasWidth(), verticalCenterGuide, true);
            newY = applyCenterGuide(newY, height, canvasHeight(), horizontalCenterGuide, false);

            node.setLayoutX(newX);
            node.setLayoutY(newY);
            event.consume();
        });

        node.setOnMouseReleased(event -> {
            hideGuides();
            viewModel.placedWidgets().updateIf(
                    w -> w.id().equals(widgetId),
                    w -> new PlacedWidget(w.id(), w.spec(), node.getLayoutX(), node.getLayoutY(), w.nickname()));
            event.consume();
        });

        // Stops the click from bubbling to the Canva's background handler, which would deselect right after selecting.
        node.setOnMouseClicked(Event::consume);
    }

    /**
     * Checks whether the dragged widget's center on one axis lines up with
     * that axis's canvas-center: within {@link #GUIDE_SHOW_DISTANCE}, shows
     * {@code guide} (thin); within the tighter {@link #GUIDE_SNAP_DISTANCE},
     * thickens it and snaps {@code position} to exactly centered. Returns the
     * (possibly snapped) position to use.
     */
    private double applyCenterGuide(double position, double size, double canvasSize, Line guide, boolean vertical) {
        double center = position + size / 2;
        double canvasCenter = canvasSize / 2;
        double distance = Math.abs(center - canvasCenter);

        if (distance > GUIDE_SHOW_DISTANCE) {
            guide.setVisible(false);
            return position;
        }

        boolean snapped = distance <= GUIDE_SNAP_DISTANCE;
        guide.setStrokeWidth(snapped ? GUIDE_WIDTH_SNAPPED : GUIDE_WIDTH_NEAR);
        guide.setVisible(true);
        if (vertical) {
            guide.setStartX(canvasCenter);
            guide.setEndX(canvasCenter);
            guide.setStartY(0);
            guide.setEndY(canvasHeight());
        } else {
            guide.setStartY(canvasCenter);
            guide.setEndY(canvasCenter);
            guide.setStartX(0);
            guide.setEndX(canvasWidth());
        }

        return snapped ? canvasCenter - size / 2 : position;
    }

    private void hideGuides() {
        verticalCenterGuide.setVisible(false);
        horizontalCenterGuide.setVisible(false);
    }

    private void applySelectionHighlight(String selectedId) {
        nodesById.forEach((id, node) -> node.setEffect(id.equals(selectedId) ? SELECTION_HIGHLIGHT : null));
    }

    private double canvasWidth() {
        return viewModel.canvasWidthState().get();
    }

    private double canvasHeight() {
        return viewModel.canvasHeightState().get();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
