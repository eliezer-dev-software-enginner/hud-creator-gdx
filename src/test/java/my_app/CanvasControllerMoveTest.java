package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a widget can be dragged around the Canva after it's been placed —
 * fixed after it turned out placing a widget left it stuck in place
 * ({@link CanvasController#makeMovable}). Runs on the real JavaFX
 * Application Thread (needed: builds real Image/Text nodes via
 * {@code WidgetViews}) but drives the installed mouse handlers directly with
 * synthetic {@link MouseEvent}s instead of a real OS drag gesture, which
 * JavaFX has no simple API to simulate outside of TestFX/Robot.
 * <p>
 * A {@code MouseEvent} built via this constructor (not dispatched through an
 * actual scene) reports {@code getSceneX()/Y() == getX()/Y()} — verified
 * empirically, not documented — which is exactly what {@code makeMovable}
 * reads, so the x/y passed to {@link #fire} below map directly to the scene
 * coordinates the drag math uses.
 * <p>
 * Widget nodes are looked up via {@link CanvasController#nodeFor} rather than
 * indexing the Canva's Pane children directly — that Pane also holds the two
 * (initially invisible) center-alignment guide lines.
 */
class CanvasControllerMoveTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void draggingAPlacedWidgetMovesItAndUpdatesTheModel() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);

            String id = viewModel.placedWidgets().get().get(0).id();
            Node widgetNode = controller.nodeFor(id);
            double startX = widgetNode.getLayoutX();
            double startY = widgetNode.getLayoutY();

            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, startX + 5, startY + 5);
            fire(widgetNode.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, startX + 55, startY + 25);
            fire(widgetNode.getOnMouseReleased(), MouseEvent.MOUSE_RELEASED, startX + 55, startY + 25);

            // Pressed at (startX+5, startY+5), dragged to (startX+55, startY+25):
            // scene delta is (+50, +20), so the node should have moved by exactly that
            // (well clear of the canvas center, so no alignment-guide snapping kicks in here).
            assertEquals(startX + 50, widgetNode.getLayoutX(), 0.01);
            assertEquals(startY + 20, widgetNode.getLayoutY(), 0.01);

            var placed = viewModel.placedWidgets().get().get(0);
            assertEquals(widgetNode.getLayoutX(), placed.x(), 0.01);
            assertEquals(widgetNode.getLayoutY(), placed.y(), 0.01);
        });
    }

    @Test
    void draggingPastTheEdgeClampsInsideTheCanvas() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(150);
            viewModel.canvasHeightState().set(100);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 150, 100);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ImageSpec("arrow"), 20, 20);

            String id = viewModel.placedWidgets().get().get(0).id();
            Node widgetNode = controller.nodeFor(id);
            double width = widgetNode.prefWidth(-1);
            double height = widgetNode.prefHeight(-1);

            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            // Drag way past the bottom-right corner.
            fire(widgetNode.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 1000, 1000);
            fire(widgetNode.getOnMouseReleased(), MouseEvent.MOUSE_RELEASED, 1000, 1000);

            assertEquals(150 - width, widgetNode.getLayoutX(), 0.01);
            assertEquals(100 - height, widgetNode.getLayoutY(), 0.01);
        });
    }

    @Test
    void draggingThroughTheCenterSnapsAndThickensTheGuides() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(200);
            viewModel.canvasHeightState().set(120);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 200, 120);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ImageSpec("arrow"), 20, 20); // 17x20, well off-center

            String id = viewModel.placedWidgets().get().get(0).id();
            Node widgetNode = controller.nodeFor(id);
            double width = widgetNode.prefWidth(-1);
            double height = widgetNode.prefHeight(-1);

            // Press at the widget's own current position, so the press offset is zero and the
            // dragged event's scene coordinates map directly to the new layoutX/Y.
            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED,
                    widgetNode.getLayoutX(), widgetNode.getLayoutY());
            // Drag its center to exactly the canvas center (100, 60).
            double sceneX = 100 - width / 2;
            double sceneY = 60 - height / 2;
            fire(widgetNode.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, sceneX, sceneY);

            assertEquals(100 - width / 2, widgetNode.getLayoutX(), 0.01, "should snap exactly centered");
            assertEquals(60 - height / 2, widgetNode.getLayoutY(), 0.01);

            var verticalGuide = controller.verticalCenterGuide();
            var horizontalGuide = controller.horizontalCenterGuide();
            assertTrue(verticalGuide.isVisible(), "vertical center guide should show once aligned");
            assertTrue(horizontalGuide.isVisible(), "horizontal center guide should show once aligned");
            assertEquals(2.5, verticalGuide.getStrokeWidth(), 0.01,
                    "guide should be thickened once actually snapped, not just close");

            fire(widgetNode.getOnMouseReleased(), MouseEvent.MOUSE_RELEASED, sceneX, sceneY);
            assertTrue(!verticalGuide.isVisible() && !horizontalGuide.isVisible(),
                    "guides should hide again once the drag ends");
        });
    }

    @Test
    void settingNicknameUpdatesTheModel() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            // Called live per keystroke by the "Properties" panel - must not touch the status
            // bar, or every character typed would spam it.
            controller.setNickname(id, "play");
            assertEquals("play", viewModel.placedWidgets().get().get(0).nickname());
            assertEquals("", viewModel.statusMessageState().get());

            controller.setNickname(id, null);
            assertEquals(null, viewModel.placedWidgets().get().get(0).nickname());
        });
    }

    @Test
    void pressingAWidgetSelectsItAndHighlightsIt() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);

            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();
            Node first = controller.nodeFor(firstId);
            Node second = controller.nodeFor(secondId);

            fire(first.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, first.getLayoutX(), first.getLayoutY());
            assertEquals(Set.of(firstId), viewModel.selectedWidgetIdsState().get());
            assertNotNull(first.getEffect(), "selected widget should be highlighted");
            assertNull(second.getEffect(), "unselected widget shouldn't be highlighted");

            fire(second.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, second.getLayoutX(), second.getLayoutY());
            assertEquals(Set.of(secondId), viewModel.selectedWidgetIdsState().get());
            assertNull(first.getEffect(), "highlight should move off the previously selected widget");
            assertNotNull(second.getEffect());
        });
    }

    @Test
    void shiftClickAddsToTheSelectionAndShiftClickingItAgainRemovesJustIt() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);

            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();
            Node first = controller.nodeFor(firstId);
            Node second = controller.nodeFor(secondId);

            fire(first.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, first.getLayoutX(), first.getLayoutY());
            assertEquals(Set.of(firstId), viewModel.selectedWidgetIdsState().get());

            fireShiftClick(second.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, second.getLayoutX(), second.getLayoutY());
            assertEquals(Set.of(firstId, secondId), viewModel.selectedWidgetIdsState().get(),
                    "shift-click should add to the selection, not replace it");
            assertNotNull(first.getEffect(), "first widget should stay highlighted too");
            assertNotNull(second.getEffect());

            fireShiftClick(second.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, second.getLayoutX(), second.getLayoutY());
            assertEquals(Set.of(firstId), viewModel.selectedWidgetIdsState().get(),
                    "shift-clicking an already-selected widget should remove just it");
        });
    }

    @Test
    void plainClickOnAnUnselectedWidgetReplacesAMultiSelection() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);
            controller.place(new WidgetSpec.ButtonSpec("play"), 300, 200);

            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();
            String thirdId = viewModel.placedWidgets().get().get(2).id();
            Node first = controller.nodeFor(firstId);
            Node second = controller.nodeFor(secondId);
            Node third = controller.nodeFor(thirdId);

            fire(first.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, first.getLayoutX(), first.getLayoutY());
            fireShiftClick(second.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, second.getLayoutX(), second.getLayoutY());
            assertEquals(Set.of(firstId, secondId), viewModel.selectedWidgetIdsState().get());

            fire(third.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, third.getLayoutX(), third.getLayoutY());
            assertEquals(Set.of(thirdId), viewModel.selectedWidgetIdsState().get(),
                    "plain click on an unselected widget should replace the whole selection");
        });
    }

    @Test
    void draggingOneOfAMultiSelectionMovesTheWholeGroupTogether() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);

            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();
            Node first = controller.nodeFor(firstId);
            Node second = controller.nodeFor(secondId);
            double firstStartX = first.getLayoutX(), firstStartY = first.getLayoutY();
            double secondStartX = second.getLayoutX(), secondStartY = second.getLayoutY();

            fire(first.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, first.getLayoutX(), first.getLayoutY());
            fireShiftClick(second.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, second.getLayoutX(), second.getLayoutY());
            assertEquals(Set.of(firstId, secondId), viewModel.selectedWidgetIdsState().get());

            // Continues the same gesture the shift-click press above started on "second" -
            // its own dragStartPositions already captured both widgets' starting spots.
            fire(second.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, second.getLayoutX() + 20, second.getLayoutY() + 10);

            assertEquals(firstStartX + 20, first.getLayoutX(), 0.01, "the other selected widget should move by the same delta");
            assertEquals(firstStartY + 10, first.getLayoutY(), 0.01);
            assertEquals(secondStartX + 20, second.getLayoutX(), 0.01, "the dragged widget itself should move normally");
            assertEquals(secondStartY + 10, second.getLayoutY(), 0.01);
        });
    }

    @Test
    void draggingAGroupClampsTogetherAtTheTightestEdgeInsteadOfIndependently() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(200);
            viewModel.canvasHeightState().set(120);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 200, 120);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ImageSpec("arrow"), 20, 20);
            controller.place(new WidgetSpec.ImageSpec("arrow"), 20, 20);

            String leftId = viewModel.placedWidgets().get().get(0).id();
            String rightId = viewModel.placedWidgets().get().get(1).id();
            Node left = controller.nodeFor(leftId);
            Node right = controller.nodeFor(rightId);
            double width = right.prefWidth(-1);

            // Position them precisely, "right" much closer to the canvas's right edge -
            // it's the tighter bound once the group is dragged that direction.
            left.setLayoutX(50);
            left.setLayoutY(20);
            right.setLayoutX(150);
            right.setLayoutY(20);
            double leftStartX = left.getLayoutX();
            double rightStartX = right.getLayoutX();

            fire(left.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, left.getLayoutX(), left.getLayoutY());
            fireShiftClick(right.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, right.getLayoutX(), right.getLayoutY());

            // Drag way past the right edge.
            fire(right.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, right.getLayoutX() + 1000, right.getLayoutY());

            double expectedDelta = 200 - width - rightStartX;
            assertEquals(rightStartX + expectedDelta, right.getLayoutX(), 0.01,
                    "the tighter widget should land exactly at the canvas edge");
            assertEquals(leftStartX + expectedDelta, left.getLayoutX(), 0.01,
                    "the other widget should keep the exact same relative offset, not clamp independently to its own (looser) edge");
        });
    }

    @Test
    void resizeHandleHidesWithMultipleSelectedAndReappearsBackToOne() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);

            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();
            Node first = controller.nodeFor(firstId);
            Node second = controller.nodeFor(secondId);

            fire(first.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, first.getLayoutX(), first.getLayoutY());
            assertTrue(controller.resizeHandle().isVisible(), "one resizable widget selected - handle should show");

            fireShiftClick(second.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, second.getLayoutX(), second.getLayoutY());
            assertFalse(controller.resizeHandle().isVisible(), "two widgets selected - handle should hide");

            fireShiftClick(first.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, first.getLayoutX(), first.getLayoutY());
            assertEquals(Set.of(secondId), viewModel.selectedWidgetIdsState().get());
            assertTrue(controller.resizeHandle().isVisible(), "back to exactly one selected - handle should reappear");
        });
    }

    @Test
    void deletingWithMultipleWidgetsSelectedRemovesAllOfThem() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Region pane = fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);
            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();

            fire(controller.nodeFor(firstId).getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            fireShiftClick(controller.nodeFor(secondId).getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            assertEquals(Set.of(firstId, secondId), viewModel.selectedWidgetIdsState().get());

            pane.getOnKeyPressed().handle(new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE, false, false, false, false));

            assertEquals(0, viewModel.placedWidgets().get().size(), "Delete should remove every selected widget");
            assertTrue(viewModel.selectedWidgetIdsState().get().isEmpty());
            assertNull(controller.nodeFor(firstId));
            assertNull(controller.nodeFor(secondId));
        });
    }

    @Test
    void removeWidgetDeletesItFromTheCanvasModelAndSelection() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Region pane = fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);
            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();

            fire(controller.nodeFor(firstId).getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            assertEquals(Set.of(firstId), viewModel.selectedWidgetIdsState().get());

            controller.removeWidget(firstId);

            assertEquals(1, viewModel.placedWidgets().get().size());
            assertEquals(secondId, viewModel.placedWidgets().get().get(0).id());
            assertNull(controller.nodeFor(firstId), "removed widget's node should be forgotten");
            assertTrue(viewModel.selectedWidgetIdsState().get().isEmpty(), "removing the selected widget should clear the selection");
            assertTrue(((javafx.scene.layout.Pane) pane).getChildren().contains(controller.nodeFor(secondId)),
                    "the other widget should still be on the canvas");
        });
    }

    @Test
    void pressingDeleteKeyRemovesTheSelectedWidget() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Region pane = fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            // Selecting (press) is what requests focus on the Pane for real key events to reach it.
            fire(controller.nodeFor(id).getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);

            pane.getOnKeyPressed().handle(new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE, false, false, false, false));

            assertEquals(0, viewModel.placedWidgets().get().size(), "Delete should have removed the selected widget");
            assertTrue(viewModel.selectedWidgetIdsState().get().isEmpty());
        });
    }

    @Test
    void pressingDeleteWithNothingSelectedDoesNothing() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Region pane = fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);

            pane.getOnKeyPressed().handle(new KeyEvent(
                    KeyEvent.KEY_PRESSED, "", "", KeyCode.DELETE, false, false, false, false));

            assertEquals(1, viewModel.placedWidgets().get().size(), "nothing was selected, nothing should be removed");
        });
    }

    @Test
    void gridCanvasTracksSizeAndTogglesWithState() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(300);
            viewModel.canvasHeightState().set(150);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 300, 150);
            CanvasController controller = new CanvasController(canva, viewModel);

            var grid = controller.gridCanvas();
            assertEquals(300, grid.getWidth(), 0.01);
            assertEquals(150, grid.getHeight(), 0.01);

            // Resizing the canvas should resize the grid too, whether or not it's currently shown.
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(200);
            assertEquals(400, grid.getWidth(), 0.01);
            assertEquals(200, grid.getHeight(), 0.01);

            // Toggling shouldn't throw either way - the actual drawn pixels aren't asserted on
            // (that's a visual concern, not a logic one), just that the state wiring doesn't blow up.
            viewModel.showingGridState().set(true);
            viewModel.showingGridState().set(false);
        });
    }

    @Test
    void gridColorIsVisibleAgainstEitherTheme() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            Canva canva = new Canva();
            fixedSizePane(canva, 300, 150);

            // The same faint color for both themes used to read as basically invisible
            // against a dark background - each theme now gets its own grid color,
            // picked from whichever theme is active when the controller (and so the
            // whole Canva) gets (re)built.
            megalodonte.base.theme.ThemeManager.setTheme(Themes.light);
            CanvasController lightController = new CanvasController(canva, viewModel);
            assertEquals(Themes.GRID_COLOR_LIGHT, lightController.gridColor());

            megalodonte.base.theme.ThemeManager.setTheme(Themes.dark);
            CanvasController darkController = new CanvasController(new Canva(), viewModel);
            assertEquals(Themes.GRID_COLOR_DARK, darkController.gridColor());
            assertTrue(darkController.gridColor().getBrightness() > lightController.gridColor().getBrightness(),
                    "dark theme's grid color should be a light color (to show up against a dark background), unlike light theme's");
        });
    }

    private static Region fixedSizePane(Canva canva, double width, double height) {
        Region pane = (Region) canva.getNode();
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
    }

    private static void fire(EventHandler<? super MouseEvent> handler, EventType<MouseEvent> type, double x, double y) {
        handler.handle(new MouseEvent(type,
                x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, false, null));
    }

    /** Same as {@link #fire}, but with {@code shiftDown} set - the additive (toggle-in-selection) modifier {@link CanvasController#updateSelectionOnPress} reads via {@code isShiftDown()}. */
    private static void fireShiftClick(EventHandler<? super MouseEvent> handler, EventType<MouseEvent> type, double x, double y) {
        handler.handle(new MouseEvent(type,
                x, y, x, y, MouseButton.PRIMARY, 1,
                true, false, false, false,
                true, false, false,
                false, false, false, null));
    }

    private static void runOnFxThreadAndWait(RunnableThrowing action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        latch.await();

        Throwable failure = error.get();
        if (failure instanceof Exception ex) throw ex;
        if (failure != null) throw new RuntimeException(failure);
    }

    @FunctionalInterface
    private interface RunnableThrowing {
        void run() throws Exception;
    }
}
