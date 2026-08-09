package my_app;

import javafx.event.Event;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import megalodonte.base.components.Component;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinModel;
import my_app.skin.render.BitmapTextView;
import my_app.skin.render.SkinImages;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import my_app.widget.render.WidgetViews;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places {@link WidgetSpec}s onto the Canva — either one at a time, centered
 * on a drop point ({@link #place}), or a whole saved layout at their exact
 * saved positions ({@link #loadLayout}) — records a matching
 * {@link PlacedWidget} in {@link HomeScreenViewModel#placedWidgets()} for
 * each, and makes every placed node draggable within the canvas afterward
 * (plain mouse-press/drag, updating {@code layoutX}/{@code layoutY} directly
 * — separate from the Dragboard-based drag used to drop a *new* widget in
 * from the palette) plus selectable — pressing a widget updates
 * {@link HomeScreenViewModel#selectedWidgetIdsState()} (Shift/Ctrl+click adds
 * to the selection instead of replacing it), which highlights every selected
 * widget here and drives the "Properties" panel (nickname editing) in
 * {@link SkinPalettePanel} — shows center-alignment guides while dragging
 * (see {@link #applyCenterGuide}), and draws a grid overlay reacting to
 * {@link HomeScreenViewModel#showingGridState()}.
 * <p>
 * {@link #place} is deliberately not tied to any actual {@code DragEvent} —
 * the real drag-and-drop handler calls it with the drop coordinates, and so
 * can a test, without needing to simulate a real OS-level drag gesture.
 */
final class CanvasController {

    private static final Color GUIDE_COLOR = Themes.GUIDE_COLOR;
    private static final double GUIDE_SHOW_DISTANCE = 15;
    private static final double GUIDE_SNAP_DISTANCE = 5;
    private static final double GUIDE_WIDTH_NEAR = 1;
    private static final double GUIDE_WIDTH_SNAPPED = 2.5;
    private static final double GRID_SPACING = 20;
    private static final double RESIZE_HANDLE_SIZE = 10;
    private static final double MIN_WIDGET_SIZE = 10;

    private final Canva canva;
    private final HomeScreenViewModel viewModel;
    private final Map<String, Node> nodesById = new HashMap<>();
    private final Map<String, BitmapTextView> textViewsById = new HashMap<>();
    /**
     * Each widget's own effect (a {@code Blend} tint for a {@code Skin.TintedDrawable}-based
     * background, or {@code null}) *before* selection is ever applied.
     * {@link #applySelectionHighlight} composes this with the selection glow
     * rather than replacing it outright — a plain {@code node.setEffect(glow)}
     * used to permanently wipe a tinted widget's color the moment it was ever
     * selected (selecting is required to reach the resize handle at all, so
     * this looked "resize breaks tinted button backgrounds" from the outside).
     */
    private final Map<String, Effect> baseEffectById = new HashMap<>();
    private final Line verticalCenterGuide = new Line();
    private final Line horizontalCenterGuide = new Line();
    private final Canvas gridCanvas = new Canvas();
    private final Rectangle resizeHandle = new Rectangle(RESIZE_HANDLE_SIZE, RESIZE_HANDLE_SIZE);
    // Same color for both themes read as "basically invisible" against a dark
    // background - resolved once here from whichever theme is active when
    // this Canva is (re)built, not a shared static (the whole HomeScreen,
    // this controller included, gets rebuilt fresh on every theme switch).
    private final Color gridColor = ThemeManager.theme() == Themes.dark ? Themes.GRID_COLOR_DARK : Themes.GRID_COLOR_LIGHT;

    CanvasController(Canva canva, HomeScreenViewModel viewModel) {
        this.canva = canva;
        this.viewModel = viewModel;
        viewModel.selectedWidgetIdsState().subscribe(this::applySelectionHighlight);
        setUpGrid();
        setUpGuides();
        setUpResizeHandle();
        setUpDeleteKey();
    }

    /**
     * Deletes every selected widget on Delete/Backspace. Keyboard events only
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

            if (!viewModel.selectedWidgetIdsState().get().isEmpty()) {
                removeSelectedWidgets();
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

        gc.setStroke(gridColor);
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

    /**
     * One reusable handle (same "add once, reposition/show instead of
     * rebuild" pattern as the two guide lines above) sitting at the selected
     * widget's bottom-right corner — dragging it resizes that widget. Hidden
     * whenever nothing's selected (see {@link #applySelectionHighlight}).
     */
    private void setUpResizeHandle() {
        resizeHandle.setFill(Themes.SELECTION_HIGHLIGHT_COLOR);
        resizeHandle.setStroke(Color.WHITE);
        resizeHandle.setCursor(Cursor.SE_RESIZE);
        resizeHandle.setVisible(false);
        ((Pane) canva.getNode()).getChildren().add(resizeHandle);

        double[] pressScenePos = new double[2];
        double[] startSize = new double[2];

        resizeHandle.setOnMousePressed(event -> {
            Node node = selectedNode();
            if (node == null) return;
            pressScenePos[0] = event.getSceneX();
            pressScenePos[1] = event.getSceneY();
            startSize[0] = node.prefWidth(-1);
            startSize[1] = node.prefHeight(-1);
            event.consume();
        });

        resizeHandle.setOnMouseDragged(event -> {
            Node node = selectedNode();
            if (node == null) return;

            double deltaX = event.getSceneX() - pressScenePos[0];
            double deltaY = event.getSceneY() - pressScenePos[1];
            double maxWidth = Math.max(MIN_WIDGET_SIZE, canvasWidth() - node.getLayoutX());
            double maxHeight = Math.max(MIN_WIDGET_SIZE, canvasHeight() - node.getLayoutY());
            double newWidth = clamp(startSize[0] + deltaX, MIN_WIDGET_SIZE, maxWidth);
            double newHeight = clamp(startSize[1] + deltaY, MIN_WIDGET_SIZE, maxHeight);

            WidgetViews.resize(node, newWidth, newHeight);
            positionResizeHandle(node);
            event.consume();
        });

        resizeHandle.setOnMouseReleased(event -> {
            String selectedId = soleSelectedId();
            Node node = selectedId == null ? null : nodesById.get(selectedId);
            if (node == null) return;

            double finalWidth = node.prefWidth(-1);
            double finalHeight = node.prefHeight(-1);
            viewModel.placedWidgets().updateIf(
                    w -> w.id().equals(selectedId),
                    w -> new PlacedWidget(w.id(), w.spec(), w.x(), w.y(), w.nickname(), finalWidth, finalHeight));
            event.consume();
        });

        // Stops the click from bubbling to the Canva's background handler, which would deselect right after a resize.
        resizeHandle.setOnMouseClicked(Event::consume);
    }

    /** The id of the sole selected widget, or {@code null} when zero or more than one is selected - resizing (and its handle) only make sense for exactly one at a time. */
    private String soleSelectedId() {
        Set<String> selected = viewModel.selectedWidgetIdsState().get();
        return selected.size() == 1 ? selected.iterator().next() : null;
    }

    private Node selectedNode() {
        String selectedId = soleSelectedId();
        return selectedId == null ? null : nodesById.get(selectedId);
    }

    private void positionResizeHandle(Node node) {
        resizeHandle.setLayoutX(node.getLayoutX() + node.prefWidth(-1) - RESIZE_HANDLE_SIZE / 2);
        resizeHandle.setLayoutY(node.getLayoutY() + node.prefHeight(-1) - RESIZE_HANDLE_SIZE / 2);
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

        finishPlacing(widget, spec, x, y, viewModel.nextWidgetId(), null, null, null);
    }

    /** Clears the canvas and re-places every widget from {@code widgets} at its saved (x, y) — used by "Load Layout". */
    void loadLayout(List<PlacedWidget> widgets) {
        clear();

        SkinModel skin = viewModel.skinState().get();
        if (skin == null) return;
        Image atlasImage = SkinImages.loadAtlasImage(skin);

        for (PlacedWidget widget : widgets) {
            Component component = WidgetViews.build(skin, atlasImage, widget.spec());
            finishPlacing(component, widget.spec(), widget.x(), widget.y(), widget.id(), widget.nickname(),
                    widget.width(), widget.height());
        }
    }

    /** Removes every widget currently on the canvas (the grid, the two guide lines, and the resize handle aren't widgets, and stay). */
    void clear() {
        ((Pane) canva.getNode()).getChildren().removeIf(
                node -> node != gridCanvas && node != verticalCenterGuide && node != horizontalCenterGuide && node != resizeHandle);
        viewModel.placedWidgets().clear();
        nodesById.clear();
        textViewsById.clear();
        baseEffectById.clear();
        viewModel.selectedWidgetIdsState().set(Set.of());
    }

    /** Sets or clears a widget's nickname — called live as the user types in the "Properties" panel, so no status-bar spam per keystroke. */
    void setNickname(String widgetId, String nicknameOrNull) {
        viewModel.placedWidgets().updateIf(
                w -> w.id().equals(widgetId),
                w -> new PlacedWidget(w.id(), w.spec(), w.x(), w.y(), nicknameOrNull, w.width(), w.height()));
    }

    /**
     * Sets a placed {@code TextButtonSpec}/{@code LabelSpec} widget's text —
     * called live as the user types in the "Properties" panel's "Text:"
     * field, same as {@link #setNickname}. A no-op for any other widget kind
     * (its spec has no {@code text} to replace).
     */
    void setText(String widgetId, String newText) {
        viewModel.placedWidgets().updateIf(
                w -> w.id().equals(widgetId),
                w -> switch (w.spec()) {
                    case WidgetSpec.TextButtonSpec s -> new PlacedWidget(
                            w.id(), new WidgetSpec.TextButtonSpec(s.styleName(), newText, s.fontColor()), w.x(), w.y(), w.nickname(), w.width(), w.height());
                    case WidgetSpec.LabelSpec s -> new PlacedWidget(
                            w.id(), new WidgetSpec.LabelSpec(s.styleName(), newText, s.fontColor()), w.x(), w.y(), w.nickname(), w.width(), w.height());
                    default -> w;
                });

        BitmapTextView textView = textViewsById.get(widgetId);
        if (textView != null) {
            textView.setText(newText);
        }
    }

    /**
     * Sets a placed {@code TextButtonSpec}/{@code LabelSpec} widget's font
     * color (a {@code #rrggbb}/{@code #rrggbbaa} hex string), or clears the
     * override (back to the skin style's own color) when {@code null} —
     * same "Properties" panel live-as-you-type wiring as {@link #setText}. A
     * no-op for any other widget kind, or for an invalid hex string (the
     * field should already have rejected it before calling this, but this
     * guards the live node update too).
     */
    void setFontColor(String widgetId, String hexColorOrNull) {
        if (hexColorOrNull != null) {
            try {
                Color.web(hexColorOrNull);
            } catch (IllegalArgumentException e) {
                return;
            }
        }

        PlacedWidget current = findPlacedWidget(widgetId);
        if (current == null) return;
        WidgetSpec newSpec = switch (current.spec()) {
            case WidgetSpec.TextButtonSpec s -> new WidgetSpec.TextButtonSpec(s.styleName(), s.text(), hexColorOrNull);
            case WidgetSpec.LabelSpec s -> new WidgetSpec.LabelSpec(s.styleName(), s.text(), hexColorOrNull);
            default -> null;
        };
        if (newSpec == null) return;

        viewModel.placedWidgets().updateIf(
                w -> w.id().equals(widgetId),
                w -> new PlacedWidget(w.id(), newSpec, w.x(), w.y(), w.nickname(), w.width(), w.height()));

        BitmapTextView textView = textViewsById.get(widgetId);
        if (textView == null) return;
        SkinModel skin = viewModel.skinState().get();
        Color resolved = hexColorOrNull != null ? Color.web(hexColorOrNull)
                : skin == null ? Color.BLACK : WidgetViews.skinFontColor(newSpec, skin);
        textView.setColor(resolved);
    }

    private PlacedWidget findPlacedWidget(String widgetId) {
        return viewModel.placedWidgets().get().stream()
                .filter(w -> w.id().equals(widgetId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sets a placed widget's size directly to ({@code width}, {@code height}) —
     * the "Properties" panel's Width/Height fields' write path, same clamping
     * (minimum size, stays inside the canvas from the widget's current x/y)
     * and end result as dragging the resize handle there. Also keeps the
     * handle glued to the new corner if this widget happens to be selected,
     * same as {@link #setUpResizeHandle}'s own drag handler.
     */
    void setSize(String widgetId, double width, double height) {
        Node node = nodesById.get(widgetId);
        if (node == null) return;

        double maxWidth = Math.max(MIN_WIDGET_SIZE, canvasWidth() - node.getLayoutX());
        double maxHeight = Math.max(MIN_WIDGET_SIZE, canvasHeight() - node.getLayoutY());
        double clampedWidth = clamp(width, MIN_WIDGET_SIZE, maxWidth);
        double clampedHeight = clamp(height, MIN_WIDGET_SIZE, maxHeight);

        WidgetViews.resize(node, clampedWidth, clampedHeight);
        if (viewModel.selectedWidgetIdsState().get().contains(widgetId)) {
            positionResizeHandle(node);
        }

        viewModel.placedWidgets().updateIf(
                w -> w.id().equals(widgetId),
                w -> new PlacedWidget(w.id(), w.spec(), w.x(), w.y(), w.nickname(), clampedWidth, clampedHeight));
    }

    /** Removes one widget from the canvas — the building block {@link #removeSelectedWidgets} (Delete/Backspace) loops, but plain enough to call directly too (tests, future "Remove" menu item, etc). */
    void removeWidget(String widgetId) {
        Node node = nodesById.remove(widgetId);
        if (node == null) return;

        ((Pane) canva.getNode()).getChildren().remove(node);
        textViewsById.remove(widgetId);
        baseEffectById.remove(widgetId);
        viewModel.placedWidgets().removeIf(w -> w.id().equals(widgetId));
        deselect(widgetId);
    }

    /** Removes every currently-selected widget — the Delete/Backspace key's bulk write path ({@link #setUpDeleteKey}). Snapshots the selection first since {@link #removeWidget} mutates it as it goes. */
    private void removeSelectedWidgets() {
        Set<String> toRemove = Set.copyOf(viewModel.selectedWidgetIdsState().get());
        toRemove.forEach(this::removeWidget);
    }

    /** Removes just {@code widgetId} from the current selection, if it's part of it — leaves the rest of a multi-selection intact, unlike replacing the whole selection with {@code null}/empty. */
    private void deselect(String widgetId) {
        Set<String> current = viewModel.selectedWidgetIdsState().get();
        if (!current.contains(widgetId)) return;
        Set<String> updated = new LinkedHashSet<>(current);
        updated.remove(widgetId);
        viewModel.selectedWidgetIdsState().set(Set.copyOf(updated));
    }

    /** The JavaFX node for a placed widget id, or null — used by tests instead of indexing into the Canva's Pane children (which also holds the guide lines). */
    Node nodeFor(String widgetId) {
        return nodesById.get(widgetId);
    }

    /** The live editable {@link BitmapTextView} for a placed {@code TextButtonSpec}/{@code LabelSpec} widget, or null (tests only). */
    BitmapTextView textViewFor(String widgetId) {
        return textViewsById.get(widgetId);
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

    /** Whichever grid color got picked for the theme active when this controller was built (tests only — {@link GraphicsContext} has no way to read a stroke color back after drawing with it). */
    Color gridColor() {
        return gridColor;
    }

    Rectangle resizeHandle() {
        return resizeHandle;
    }

    private void finishPlacing(Component component, WidgetSpec spec, double x, double y, String id, String nickname,
                                Double width, Double height) {
        Node node = component.getNode();
        if (width != null && height != null) {
            WidgetViews.resize(node, width, height);
        }
        canva.child(component, x, y);
        nodesById.put(id, node);
        // A plain ButtonSpec/ImageSpec's node IS its background - if that
        // background is a Skin.TintedDrawable alias, WidgetViews.build()
        // already gave it a Blend tint effect here. TextButtonSpec/LabelSpec's
        // top-level node is a StackPane whose *child* carries the tint
        // instead, so this is always null for those - nothing to preserve,
        // and nothing this widget's own selection highlight could clobber.
        baseEffectById.put(id, node.getEffect());
        BitmapTextView textView = WidgetViews.bitmapTextViewOf(node);
        if (textView != null) {
            textViewsById.put(id, textView);
        }
        viewModel.placedWidgets().add(new PlacedWidget(id, spec, x, y, nickname, width, height));
        makeMovable(node, id);
    }

    /**
     * Lets an already-placed {@code node} be selected (click/press) and
     * dragged around the canvas by mouse. Every currently-selected widget
     * moves together as a group (not just {@code node}) — {@code node}'s own
     * handlers are the only ones that fire during this gesture (the mouse
     * only ever presses on one node at a time), so they drive every other
     * selected widget's position too, via {@link #nodesById}.
     */
    private void makeMovable(Node node, String widgetId) {
        node.setCursor(Cursor.MOVE);

        double[] pressScenePos = new double[2];
        // Each selected widget's own (layoutX, layoutY) at the moment the
        // drag started - a group drag applies the exact same delta to every
        // one of these, so relative positions never drift apart.
        Map<String, double[]> dragStartPositions = new HashMap<>();

        node.setOnMousePressed(event -> {
            updateSelectionOnPress(widgetId, event.isShiftDown() || event.isShortcutDown());
            ((Pane) canva.getNode()).requestFocus(); // so the Delete/Backspace handler in setUpDeleteKey actually receives key events

            pressScenePos[0] = event.getSceneX();
            pressScenePos[1] = event.getSceneY();
            dragStartPositions.clear();
            for (String id : viewModel.selectedWidgetIdsState().get()) {
                Node selectedNode = nodesById.get(id);
                if (selectedNode != null) {
                    dragStartPositions.put(id, new double[]{selectedNode.getLayoutX(), selectedNode.getLayoutY()});
                }
            }
            event.consume();
        });

        node.setOnMouseDragged(event -> {
            if (dragStartPositions.isEmpty()) return;

            double rawDeltaX = event.getSceneX() - pressScenePos[0];
            double rawDeltaY = event.getSceneY() - pressScenePos[1];
            double[] delta = clampGroupDelta(dragStartPositions, rawDeltaX, rawDeltaY);

            // Center-alignment guides only make sense for a single widget's
            // own center - skip snapping (and hide the guides) for a group.
            if (dragStartPositions.size() == 1) {
                double[] start = dragStartPositions.get(widgetId);
                double width = node.prefWidth(-1);
                double height = node.prefHeight(-1);
                double snappedX = applyCenterGuide(start[0] + delta[0], width, canvasWidth(), verticalCenterGuide, true);
                double snappedY = applyCenterGuide(start[1] + delta[1], height, canvasHeight(), horizontalCenterGuide, false);
                delta[0] = snappedX - start[0];
                delta[1] = snappedY - start[1];
            } else {
                hideGuides();
            }

            for (var entry : dragStartPositions.entrySet()) {
                Node selectedNode = nodesById.get(entry.getKey());
                if (selectedNode == null) continue;
                selectedNode.setLayoutX(entry.getValue()[0] + delta[0]);
                selectedNode.setLayoutY(entry.getValue()[1] + delta[1]);
            }
            if (dragStartPositions.size() == 1) {
                positionResizeHandle(node);
            }
            event.consume();
        });

        node.setOnMouseReleased(event -> {
            hideGuides();
            for (String id : dragStartPositions.keySet()) {
                Node selectedNode = nodesById.get(id);
                if (selectedNode == null) continue;
                double finalX = selectedNode.getLayoutX();
                double finalY = selectedNode.getLayoutY();
                viewModel.placedWidgets().updateIf(
                        w -> w.id().equals(id),
                        w -> new PlacedWidget(w.id(), w.spec(), finalX, finalY, w.nickname(), w.width(), w.height()));
            }
            event.consume();
        });

        // Stops the click from bubbling to the Canva's background handler, which would deselect right after selecting.
        node.setOnMouseClicked(Event::consume);
    }

    /**
     * Updates the selection for a mouse-press on {@code widgetId}:
     * Shift/Ctrl(Cmd)+click toggles it in or out of the current selection; a
     * plain click on a widget that's *not* already selected replaces the
     * selection with just it; a plain click on a widget that's *already*
     * selected leaves the (possibly multi-widget) selection alone, so the
     * drag that follows can move the whole group together instead of
     * collapsing it to one widget.
     */
    private void updateSelectionOnPress(String widgetId, boolean additive) {
        Set<String> current = viewModel.selectedWidgetIdsState().get();
        if (additive) {
            Set<String> updated = new LinkedHashSet<>(current);
            if (!updated.remove(widgetId)) {
                updated.add(widgetId);
            }
            viewModel.selectedWidgetIdsState().set(Set.copyOf(updated));
        } else if (!current.contains(widgetId)) {
            viewModel.selectedWidgetIdsState().set(Set.of(widgetId));
        }
    }

    /**
     * Clamps a proposed {@code (deltaX, deltaY)} drag so that *every* widget
     * in {@code startPositions} stays fully inside the canvas — the tightest
     * bound across the whole group wins, so a multi-widget drag preserves
     * every member's position relative to the others exactly, rather than
     * clamping each one independently (which would let the group drift out
     * of alignment as soon as any single widget hit an edge).
     */
    private double[] clampGroupDelta(Map<String, double[]> startPositions, double deltaX, double deltaY) {
        double minDx = Double.NEGATIVE_INFINITY, maxDx = Double.POSITIVE_INFINITY;
        double minDy = Double.NEGATIVE_INFINITY, maxDy = Double.POSITIVE_INFINITY;

        for (var entry : startPositions.entrySet()) {
            Node selectedNode = nodesById.get(entry.getKey());
            if (selectedNode == null) continue;
            double startX = entry.getValue()[0];
            double startY = entry.getValue()[1];
            double width = selectedNode.prefWidth(-1);
            double height = selectedNode.prefHeight(-1);

            minDx = Math.max(minDx, -startX);
            maxDx = Math.min(maxDx, canvasWidth() - width - startX);
            minDy = Math.max(minDy, -startY);
            maxDy = Math.min(maxDy, canvasHeight() - height - startY);
        }

        return new double[]{
                clamp(deltaX, minDx, Math.max(minDx, maxDx)),
                clamp(deltaY, minDy, Math.max(minDy, maxDy))
        };
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

    /**
     * Composes the selection glow with each widget's own {@link #baseEffectById}
     * effect instead of replacing it outright — {@code node.setEffect(glow)}
     * used to permanently wipe a plain {@code ButtonSpec}'s
     * {@code Skin.TintedDrawable} tint (see {@link #baseEffectById}) the
     * moment it was ever selected, since selecting stayed applied via
     * {@code DropShadow.setInput} chains the glow *on top of* the tint
     * (rendered first, then the shadow around the result), and deselecting
     * restores the tint alone instead of {@code null}. Every widget in
     * {@code selectedIds} gets the glow, not just one — the resize handle,
     * though, only ever applies to a *single* selected widget (see
     * {@link #soleSelectedId}), so it hides for zero or multiple selected.
     */
    private void applySelectionHighlight(Set<String> selectedIds) {
        nodesById.forEach((id, node) -> {
            Effect base = baseEffectById.get(id);
            if (selectedIds.contains(id)) {
                DropShadow glow = new DropShadow(8, Themes.SELECTION_HIGHLIGHT_COLOR);
                glow.setInput(base);
                node.setEffect(glow);
            } else {
                node.setEffect(base);
            }
        });

        Node selected = selectedNode();
        String selectedId = soleSelectedId();
        if (selected == null || selectedId == null || !isResizable(selectedId)) {
            resizeHandle.setVisible(false);
            return;
        }
        positionResizeHandle(selected);
        resizeHandle.setVisible(true);
        resizeHandle.toFront();
    }

    /**
     * A {@code LabelSpec} ("Texto" in the Palette) whose style declares no
     * {@code background} has no resizable visual at all - dragging its
     * resize handle only grew/shrank the invisible {@code StackPane} box
     * around the {@link BitmapTextView}'s own glyph-fit {@code Canvas},
     * which never reflows to fill it (matching real Scene2D
     * {@code Label.setSize()} with wrap off), so the handle never actually
     * changed anything visible. Every other kind - including a {@code Label}
     * whose style *does* declare a {@code background} (e.g. a bordered/framed
     * style) - keeps a real resizable background. See {@link WidgetViews#isResizable}.
     */
    private boolean isResizable(String widgetId) {
        Node node = nodesById.get(widgetId);
        return node != null && WidgetViews.isResizable(node);
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
