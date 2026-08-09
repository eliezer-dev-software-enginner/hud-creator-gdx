package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The "Grid" overlay used to be purely visual - moving/resizing a widget
 * never actually aligned to it. {@link CanvasController#makeMovable}/
 * {@link CanvasController#setUpResizeHandle}'s drag handlers now round to
 * the nearest {@link CanvasController#GRID_SPACING} multiple whenever
 * {@link HomeScreenViewModel#showingGridState()} is on - reusing the same
 * checkbox that already toggles the grid's visibility, rather than adding a
 * second control. Grid snapping only fills in on an axis the alignment
 * guide (see {@link CanvasControllerAlignmentGuideTest}) didn't already
 * claim - a guide match is more specific/intentional than a generic grid
 * line.
 */
class CanvasControllerSnapToGridTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void draggingWithGridShowingSnapsToTheNearestGridLine() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(500);
            viewModel.canvasHeightState().set(400);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            viewModel.showingGridState().set(true);

            Canva canva = new Canva();
            fixedSizePane(canva, 500, 400);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Node node = controller.nodeFor(id);
            // Canvas center is (250, 200) here - far from where this drags to, so no alignment guide interferes.
            node.setLayoutX(10);
            node.setLayoutY(10);

            fire(node.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 10, 10);
            fire(node.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 33, 57);

            assertEquals(40, node.getLayoutX(), 0.01, "33 should round to the nearest grid line (40)");
            assertEquals(60, node.getLayoutY(), 0.01, "57 should round to the nearest grid line (60)");
        });
    }

    @Test
    void draggingWithGridHiddenDoesNotSnap() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(500);
            viewModel.canvasHeightState().set(400);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            viewModel.showingGridState().set(false);

            Canva canva = new Canva();
            fixedSizePane(canva, 500, 400);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Node node = controller.nodeFor(id);
            node.setLayoutX(10);
            node.setLayoutY(10);

            fire(node.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 10, 10);
            fire(node.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 33, 57);

            assertEquals(33, node.getLayoutX(), 0.01, "grid off - should land exactly where dragged, unsnapped");
            assertEquals(57, node.getLayoutY(), 0.01);
        });
    }

    @Test
    void resizingWithGridShowingSnapsToTheNearestGridLine() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(500);
            viewModel.canvasHeightState().set(400);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            viewModel.showingGridState().set(true);

            Canva canva = new Canva();
            fixedSizePane(canva, 500, 400);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            controller.setSize(id, 100, 80); // known, already grid-aligned starting size
            Node node = controller.nodeFor(id);
            node.setLayoutX(20);
            node.setLayoutY(20);

            fire(node.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, node.getLayoutX(), node.getLayoutY());
            Rectangle handle = controller.resizeHandle();
            fire(handle.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            fire(handle.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 33, 57);

            assertEquals(140, node.prefWidth(-1), 0.01, "100+33=133 should round to the nearest grid line (140)");
            assertEquals(140, node.prefHeight(-1), 0.01, "80+57=137 should round to the nearest grid line (140)");
        });
    }

    @Test
    void alignmentGuideTakesPriorityOverGridSnapping() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            viewModel.showingGridState().set(true);

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 150);
            String otherId = viewModel.placedWidgets().get().get(0).id();
            String draggedId = viewModel.placedWidgets().get().get(1).id();
            Node other = controller.nodeFor(otherId);
            Node dragged = controller.nodeFor(draggedId);

            other.setLayoutX(53); // deliberately not a grid multiple
            other.setLayoutY(50);
            dragged.setLayoutX(200);
            dragged.setLayoutY(150);

            fire(dragged.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 200, 150);
            // Drag exactly onto other's left edge (53, not a grid multiple).
            fire(dragged.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 53, 150);

            assertEquals(53, dragged.getLayoutX(), 0.01,
                    "the alignment guide's exact match should win over the nearest grid line (60)");
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
