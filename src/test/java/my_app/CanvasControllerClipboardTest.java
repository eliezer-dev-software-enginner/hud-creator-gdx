package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Ctrl+C}/{@code Ctrl+V} (copy/paste through an in-app clipboard - not
 * the OS one) and {@code Ctrl+D} (duplicate in one step) used to not exist at
 * all - repeating an already-configured widget meant dragging a fresh one
 * from the Palette and reconfiguring it from scratch. Verifies
 * {@link CanvasController#copySelectedWidgets}/{@link CanvasController#pasteClipboard}/
 * {@link CanvasController#duplicateSelectedWidgets} and their keyboard wiring
 * in {@link CanvasController#setUpKeyboardShortcuts}.
 */
class CanvasControllerClipboardTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void copyThenPasteCreatesANewOffsetWidget() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Pane pane = (Pane) fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String originalId = viewModel.placedWidgets().get().get(0).id();
            PlacedWidget original = viewModel.placedWidgets().get().get(0);

            select(controller, originalId);
            fireCtrl(pane, KeyCode.C);
            assertEquals(1, controller.clipboardSize());

            fireCtrl(pane, KeyCode.V);

            assertEquals(2, viewModel.placedWidgets().get().size(), "paste should add a new widget, not replace the original");
            PlacedWidget pasted = viewModel.placedWidgets().get().stream()
                    .filter(w -> !w.id().equals(originalId))
                    .findFirst().orElseThrow();
            assertEquals(original.x() + 20, pasted.x(), 0.01, "pasted widget should be offset from the original");
            assertEquals(original.y() + 20, pasted.y(), 0.01);
            assertEquals(original.spec(), pasted.spec(), "pasted widget should keep the same spec");
            assertEquals(Set.of(pasted.id()), viewModel.selectedWidgetIdsState().get(), "paste should select the new copy");
        });
    }

    @Test
    void pastingNeverKeepsTheOriginalsNickname() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Pane pane = (Pane) fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String originalId = viewModel.placedWidgets().get().get(0).id();
            controller.setNickname(originalId, "play-button");

            select(controller, originalId);
            fireCtrl(pane, KeyCode.C);
            fireCtrl(pane, KeyCode.V);

            PlacedWidget pasted = viewModel.placedWidgets().get().stream()
                    .filter(w -> !w.id().equals(originalId))
                    .findFirst().orElseThrow();
            assertNull(pasted.nickname(), "duplicating a nickname would make the libGDX-side actor lookup ambiguous");
        });
    }

    @Test
    void pasteWithNothingCopiedDoesNothing() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Pane pane = (Pane) fixedSizePane(canva, 400, 300);
            CanvasController controller = new CanvasController(canva, viewModel);

            assertEquals(0, controller.clipboardSize());
            fireCtrl(pane, KeyCode.V);

            assertEquals(0, viewModel.placedWidgets().get().size(), "nothing was ever copied, nothing should be pasted");
        });
    }

    @Test
    void copyingMultipleWidgetsPreservesTheirRelativeArrangementOnPaste() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Pane pane = (Pane) fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);
            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();
            PlacedWidget firstOriginal = viewModel.placedWidgets().get().get(0);
            PlacedWidget secondOriginal = viewModel.placedWidgets().get().get(1);

            select(controller, firstId);
            fireShiftClick(controller.nodeFor(secondId));
            fireCtrl(pane, KeyCode.C);
            assertEquals(2, controller.clipboardSize());

            fireCtrl(pane, KeyCode.V);

            assertEquals(4, viewModel.placedWidgets().get().size());
            PlacedWidget firstCopy = viewModel.placedWidgets().get().stream()
                    .filter(w -> !w.id().equals(firstId) && !w.id().equals(secondId))
                    .filter(w -> w.spec().equals(firstOriginal.spec()))
                    .findFirst().orElseThrow();
            PlacedWidget secondCopy = viewModel.placedWidgets().get().stream()
                    .filter(w -> !w.id().equals(firstId) && !w.id().equals(secondId))
                    .filter(w -> w.spec().equals(secondOriginal.spec()))
                    .findFirst().orElseThrow();

            assertEquals(firstOriginal.x() + 20, firstCopy.x(), 0.01);
            assertEquals(firstOriginal.y() + 20, firstCopy.y(), 0.01);
            assertEquals(secondOriginal.x() + 20, secondCopy.x(), 0.01,
                    "each pasted widget should be offset from its own original, keeping the group's relative arrangement");
            assertEquals(secondOriginal.y() + 20, secondCopy.y(), 0.01);
        });
    }

    @Test
    void duplicateCopiesTheSelectionWithoutTouchingTheClipboard() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Pane pane = (Pane) fixedSizePane(canva, 400, 300);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            controller.place(new WidgetSpec.ButtonSpec("toggle"), 200, 100);
            String firstId = viewModel.placedWidgets().get().get(0).id();
            String secondId = viewModel.placedWidgets().get().get(1).id();

            // Copy "second" first, so the clipboard holds it.
            select(controller, secondId);
            fireCtrl(pane, KeyCode.C);
            assertEquals(1, controller.clipboardSize());

            // Now select "first" and duplicate it - should not disturb the clipboard.
            select(controller, firstId);
            fireCtrl(pane, KeyCode.D);

            assertEquals(3, viewModel.placedWidgets().get().size(), "duplicate should add exactly one new widget");
            assertEquals(1, controller.clipboardSize(), "duplicate shouldn't touch the clipboard");

            // Pasting afterward should still paste "second" (the actual clipboard contents), not "first".
            fireCtrl(pane, KeyCode.V);
            assertEquals(4, viewModel.placedWidgets().get().size());
        });
    }

    @Test
    void pasteClampsToStayInsideTheCanvas() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(150);
            viewModel.canvasHeightState().set(100);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            Canva canva = new Canva();
            Pane pane = (Pane) fixedSizePane(canva, 150, 100);

            CanvasController controller = new CanvasController(canva, viewModel);
            controller.place(new WidgetSpec.ImageSpec("arrow"), 145, 95); // right against the corner
            String originalId = viewModel.placedWidgets().get().get(0).id();
            Node originalNode = controller.nodeFor(originalId);
            double width = originalNode.prefWidth(-1);
            double height = originalNode.prefHeight(-1);

            select(controller, originalId);
            fireCtrl(pane, KeyCode.C);
            fireCtrl(pane, KeyCode.V);

            PlacedWidget pasted = viewModel.placedWidgets().get().stream()
                    .filter(w -> !w.id().equals(originalId))
                    .findFirst().orElseThrow();
            assertEquals(150 - width, pasted.x(), 0.01, "pasted copy shouldn't land outside the canvas");
            assertEquals(100 - height, pasted.y(), 0.01);
        });
    }

    private static void select(CanvasController controller, String widgetId) {
        Node node = controller.nodeFor(widgetId);
        node.getOnMousePressed().handle(new MouseEvent(MouseEvent.MOUSE_PRESSED,
                node.getLayoutX(), node.getLayoutY(), node.getLayoutX(), node.getLayoutY(),
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, false, null));
    }

    private static void fireShiftClick(Node node) {
        node.getOnMousePressed().handle(new MouseEvent(MouseEvent.MOUSE_PRESSED,
                node.getLayoutX(), node.getLayoutY(), node.getLayoutX(), node.getLayoutY(),
                MouseButton.PRIMARY, 1,
                true, false, false, false,
                true, false, false,
                false, false, false, null));
    }

    private static void fireCtrl(Pane pane, KeyCode code) {
        pane.getOnKeyPressed().handle(new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", code, false, true, false, false));
    }

    private static Region fixedSizePane(Canva canva, double width, double height) {
        Region pane = (Region) canva.getNode();
        pane.setMinSize(width, height);
        pane.setPrefSize(width, height);
        pane.setMaxSize(width, height);
        return pane;
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
