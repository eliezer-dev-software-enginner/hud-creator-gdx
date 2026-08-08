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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the resize handle added in {@link CanvasController#setUpResizeHandle}
 * — every widget used to be stuck at whatever size the skin's own drawable/
 * font preferred. Same synthetic-{@link MouseEvent} technique as
 * {@link CanvasControllerMoveTest} (real drag gestures aren't simulatable
 * outside TestFX/Robot), driving the handle's installed handlers directly.
 */
class CanvasControllerResizeTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");
    // The bundled example skin has no LabelStyle at all - flat-earth does.
    private static final Path LABEL_CAPABLE_SKIN = Path.of(
            "..", "gdx-skins", "flat-earth", "skin", "flat-earth-ui.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void draggingTheHandleResizesTheSelectedWidgetAndUpdatesTheModel() throws Exception {
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
            double startWidth = widgetNode.prefWidth(-1);
            double startHeight = widgetNode.prefHeight(-1);

            // Select it first - the handle only acts on whatever's selected.
            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, widgetNode.getLayoutX(), widgetNode.getLayoutY());

            Rectangle handle = controller.resizeHandle();
            assertTrue(handle.isVisible(), "handle should show once a widget is selected");

            fire(handle.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            fire(handle.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 30, 15);

            assertEquals(startWidth + 30, widgetNode.prefWidth(-1), 0.01, "node should resize live while dragging");
            assertEquals(startHeight + 15, widgetNode.prefHeight(-1), 0.01);

            fire(handle.getOnMouseReleased(), MouseEvent.MOUSE_RELEASED, 30, 15);

            var placed = viewModel.placedWidgets().get().get(0);
            assertEquals(startWidth + 30, placed.width(), 0.01, "resize should be committed to the model on release");
            assertEquals(startHeight + 15, placed.height(), 0.01);
        });
    }

    @Test
    void resizingClampsToAMinimumSize() throws Exception {
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

            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, widgetNode.getLayoutX(), widgetNode.getLayoutY());

            Rectangle handle = controller.resizeHandle();
            fire(handle.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            // Drag way past shrinking it to nothing/negative.
            fire(handle.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, -1000, -1000);

            assertEquals(10, widgetNode.prefWidth(-1), 0.01, "should clamp to the minimum widget size, not shrink to zero/negative");
            assertEquals(10, widgetNode.prefHeight(-1), 0.01);
        });
    }

    @Test
    void resizingClampsToTheCanvasBounds() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(150);
            viewModel.canvasHeightState().set(100);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 150, 100);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 20, 20);
            String id = viewModel.placedWidgets().get().get(0).id();
            Node widgetNode = controller.nodeFor(id);
            double x = widgetNode.getLayoutX();
            double y = widgetNode.getLayoutY();

            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, x, y);

            Rectangle handle = controller.resizeHandle();
            fire(handle.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, 0, 0);
            // Drag way past the canvas's far edge.
            fire(handle.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, 1000, 1000);

            assertEquals(150 - x, widgetNode.prefWidth(-1), 0.01, "shouldn't be able to resize past the canvas edge");
            assertEquals(100 - y, widgetNode.prefHeight(-1), 0.01);
        });
    }

    @Test
    void handleTracksTheWidgetWhileItsBeingDraggedAndHidesOnDeselect() throws Exception {
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
            Rectangle handle = controller.resizeHandle();

            assertFalse(handle.isVisible(), "nothing selected yet - handle should be hidden");

            double startX = widgetNode.getLayoutX();
            double startY = widgetNode.getLayoutY();
            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, startX + 5, startY + 5);
            assertTrue(handle.isVisible());
            double handleXAfterSelect = handle.getLayoutX();

            fire(widgetNode.getOnMouseDragged(), MouseEvent.MOUSE_DRAGGED, startX + 55, startY + 5);
            assertTrue(handle.getLayoutX() > handleXAfterSelect, "handle should follow the widget while it's being moved");

            controller.removeWidget(id);
            assertFalse(handle.isVisible(), "removing the selected widget should hide the handle too");
        });
    }

    @Test
    void theHandleStaysHiddenForALabelWidget() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(LABEL_CAPABLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.LabelSpec("default", "Lives"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Node widgetNode = controller.nodeFor(id);
            Rectangle handle = controller.resizeHandle();

            fire(widgetNode.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, widgetNode.getLayoutX(), widgetNode.getLayoutY());

            assertFalse(handle.isVisible(), "a LabelSpec's Canvas is always glyph-sized and never reflows - no resize handle makes sense");
        });
    }

    @Test
    void setSizeResizesDirectlyAndUpdatesTheModel() throws Exception {
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

            controller.setSize(id, 150, 75);

            assertEquals(150, widgetNode.prefWidth(-1), 0.01);
            assertEquals(75, widgetNode.prefHeight(-1), 0.01);

            var placed = viewModel.placedWidgets().get().get(0);
            assertEquals(150, placed.width(), 0.01);
            assertEquals(75, placed.height(), 0.01);
        });
    }

    @Test
    void setSizeClampsTheSameWayTheHandleDoes() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(150);
            viewModel.canvasHeightState().set(100);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 150, 100);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 20, 20);
            String id = viewModel.placedWidgets().get().get(0).id();
            Node widgetNode = controller.nodeFor(id);
            double x = widgetNode.getLayoutX();
            double y = widgetNode.getLayoutY();

            controller.setSize(id, -5, 5000);

            assertEquals(10, widgetNode.prefWidth(-1), 0.01, "shouldn't shrink below the minimum widget size");
            assertEquals(100 - y, widgetNode.prefHeight(-1), 0.01, "shouldn't grow past the canvas edge");
        });
    }

    @Test
    void editingOneSizeFieldPreservesTheOther() throws Exception {
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
            double startHeight = widgetNode.prefHeight(-1);

            // Simulates typing only into the Properties panel's Width field.
            controller.setSize(id, 200, widgetNode.prefHeight(-1));

            assertEquals(200, widgetNode.prefWidth(-1), 0.01);
            assertEquals(startHeight, widgetNode.prefHeight(-1), 0.01, "height shouldn't change from a width-only edit");
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
