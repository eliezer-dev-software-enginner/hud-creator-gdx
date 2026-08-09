package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The alignment guide used to only ever snap a dragged widget to the
 * canvas's own center ({@code CanvasController.applyCenterGuide}) - no way
 * to line one widget up against another already on the Canva, which is most
 * of what "alignment guides" mean in a real editor. Verifies the
 * generalized {@code CanvasController.applyAlignmentGuide}: matching left
 * edges, centers, and right edges against every *other* placed widget, and
 * that whichever candidate (canvas center or a widget edge) is closest
 * wins when more than one is in range.
 */
class CanvasControllerAlignmentGuideTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void snapsToAnotherWidgetsLeftEdge() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 150);
            String otherId = viewModel.placedWidgets().get().get(0).id();
            String draggedId = viewModel.placedWidgets().get().get(1).id();
            Node other = controller.nodeFor(otherId);
            Node dragged = controller.nodeFor(draggedId);

            other.setLayoutX(50);
            other.setLayoutY(50);
            dragged.setLayoutX(200);
            dragged.setLayoutY(150);

            fire(dragged.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 200, 150);
            // Drag exactly onto other's left edge (50) - an exact (distance-0) match can't lose to any other candidate.
            fire(dragged.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 50, 150);

            assertEquals(50, dragged.getLayoutX(), 0.01, "should snap its own left edge to the other widget's left edge");
            assertTrue(controller.verticalCenterGuide().isVisible());
            assertEquals(50, controller.verticalCenterGuide().getStartX(), 0.01);
            assertEquals(2.5, controller.verticalCenterGuide().getStrokeWidth(), 0.01, "exact match - guide should be thickened (snapped)");
        });
    }

    @Test
    void snapsToAnotherWidgetsCenter() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 150);
            String otherId = viewModel.placedWidgets().get().get(0).id();
            String draggedId = viewModel.placedWidgets().get().get(1).id();

            // Forces distinct, known widths - with equal widths, aligning centers
            // is mathematically identical to aligning lefts (same shift), which
            // would make this indistinguishable from the left-edge case above.
            controller.setSize(otherId, 40, 30);
            controller.setSize(draggedId, 100, 30);
            Node other = controller.nodeFor(otherId);
            Node dragged = controller.nodeFor(draggedId);

            other.setLayoutX(50);
            other.setLayoutY(50);
            double otherCenterX = 50 + 40.0 / 2; // 70
            double targetX = otherCenterX - 100.0 / 2; // dragged's center (targetX + 50) lands exactly on 70

            dragged.setLayoutX(200);
            dragged.setLayoutY(150);

            fire(dragged.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 200, 150);
            fire(dragged.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, targetX, 150);

            assertEquals(targetX, dragged.getLayoutX(), 0.01, "should snap so its own center lands on the other widget's center");
            assertTrue(controller.verticalCenterGuide().isVisible());
            assertEquals(otherCenterX, controller.verticalCenterGuide().getStartX(), 0.01);
        });
    }

    @Test
    void snapsToAnotherWidgetsRightEdge() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 150);
            String otherId = viewModel.placedWidgets().get().get(0).id();
            String draggedId = viewModel.placedWidgets().get().get(1).id();

            // Distinct widths again, and "other" positioned far enough right that
            // aligning the (wider) dragged widget's right edge to it doesn't need
            // a negative x - see the center-edge test above for why equal widths
            // would make this ambiguous with the other role-based tests.
            controller.setSize(otherId, 40, 30);
            controller.setSize(draggedId, 100, 30);
            Node other = controller.nodeFor(otherId);
            Node dragged = controller.nodeFor(draggedId);

            other.setLayoutX(200);
            other.setLayoutY(150);
            double otherRightX = 200 + 40; // 240
            double targetX = otherRightX - 100; // dragged's right edge (targetX + 100) lands exactly on 240

            dragged.setLayoutX(50);
            dragged.setLayoutY(150);

            fire(dragged.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 50, 150);
            fire(dragged.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, targetX, 150);

            assertEquals(targetX, dragged.getLayoutX(), 0.01, "should snap so its own right edge lands on the other widget's right edge");
            assertTrue(controller.verticalCenterGuide().isVisible());
            assertEquals(otherRightX, controller.verticalCenterGuide().getStartX(), 0.01);
        });
    }

    @Test
    void noGuideWhenNothingIsCloseEnough() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            viewModel.showingGridState().set(false); // isolates this to alignment-guide behavior - grid snapping is a separate fallback, covered by its own tests

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 250, 150);
            String otherId = viewModel.placedWidgets().get().get(0).id();
            String draggedId = viewModel.placedWidgets().get().get(1).id();
            Node other = controller.nodeFor(otherId);
            Node dragged = controller.nodeFor(draggedId);

            // Canvas center (200) and "other" both sit well clear of the 20-35
            // range this drag moves through - no candidate on either axis
            // should be within GUIDE_SHOW_DISTANCE (15).
            other.setLayoutX(250);
            other.setLayoutY(250);
            dragged.setLayoutX(20);
            dragged.setLayoutY(20);

            fire(dragged.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 20, 20);
            fire(dragged.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 35, 35);

            assertEquals(35, dragged.getLayoutX(), 0.01, "no candidate in range - should move exactly where dragged, unsnapped");
            assertEquals(35, dragged.getLayoutY(), 0.01);
            assertFalse(controller.verticalCenterGuide().isVisible());
            assertFalse(controller.horizontalCenterGuide().isVisible());
        });
    }

    @Test
    void closerCandidateWinsWhenBothCanvasCenterAndAWidgetEdgeAreInRange() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(260); // canvas center = 130
            viewModel.canvasHeightState().set(200);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 260, 200);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 150, 100);
            String otherId = viewModel.placedWidgets().get().get(0).id();
            String draggedId = viewModel.placedWidgets().get().get(1).id();

            // Forces exact, known widths so both candidate distances below are fully deterministic.
            controller.setSize(otherId, 40, 30);
            controller.setSize(draggedId, 100, 30);
            Node other = controller.nodeFor(otherId);
            Node dragged = controller.nodeFor(draggedId);

            other.setLayoutX(100); // other's center = 120
            other.setLayoutY(20);
            dragged.setLayoutY(120);
            dragged.setLayoutX(5);

            // Dragging so the widget's proposed center is 122: distance to the
            // other widget's center (120) is 2; distance to the canvas center
            // (130) is 8. Both are within GUIDE_SHOW_DISTANCE (15) - the closer
            // one (the other widget) must win.
            fire(dragged.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 5, 120);
            fire(dragged.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 72, 120);

            assertEquals(120, controller.verticalCenterGuide().getStartX(), 0.01,
                    "the other widget's center (distance 2) should win over the canvas center (distance 8)");
            assertEquals(70, dragged.getLayoutX(), 0.01, "should snap so its own center lands exactly on 120, not 130");
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
