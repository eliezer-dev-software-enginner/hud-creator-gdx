package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.effect.Blend;
import javafx.scene.effect.DropShadow;
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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Selecting a plain {@code ButtonSpec} backed by a {@code Skin.TintedDrawable}
 * region used to permanently lose its color the moment it was ever selected
 * — {@code CanvasController.applySelectionHighlight} replaced the node's
 * effect outright ({@code node.setEffect(glow)}), clobbering the {@link Blend}
 * tint {@link my_app.skin.render.DrawableView#applyTint} had applied; on
 * deselect it went to {@code null} instead of back to the tint. Selecting is
 * required to reach the resize handle at all, so the bug report ("resizing
 * makes the background disappear") was really "selecting" wearing a resize
 * costume. Fixed by composing the glow around the widget's own effect via
 * {@code DropShadow.setInput(...)} instead of replacing it.
 * <p>
 * Fixing this the naive way (tracking {@code Node.boundsInLocalProperty()}
 * to keep the tint's {@code ColorInput} sized to the node) caused a real
 * {@code StackOverflowError} — {@code boundsInLocal} includes the node's own
 * effect, so resizing the {@code ColorInput} changed the effect's bounds,
 * which changed {@code boundsInLocal}, which re-fired the same listener.
 * {@link my_app.skin.render.DrawableView#applyTint} now tracks
 * {@code layoutBoundsProperty()} instead (defined to exclude effect/clip),
 * which is what actually stops the recursion — not tested for directly here
 * (a real infinite recursion isn't something you can catch and assert on
 * usefully), but the select/resize/deselect sequence below would never
 * finish executing at all if that regressed.
 */
class CanvasControllerSelectionEffectTest {

    // The bundled example skin has no TintedDrawable at all - flat-earth does
    // (already used elsewhere this session for the same reason).
    private static final Path TINTED_SKIN = Path.of(
            "..", "gdx-skins", "flat-earth", "skin", "flat-earth-ui.json");
    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void selectingATintedButtonComposesTheGlowInsteadOfReplacingTheTint() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(TINTED_SKIN));

            Canva canva = new Canva();
            fixedSizePane(canva, 400, 300);
            CanvasController controller = new CanvasController(canva, viewModel);
            // "close" resolves to button-close-c, a Skin.TintedDrawable alias in flat-earth.
            controller.place(new WidgetSpec.ButtonSpec("close"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Node node = controller.nodeFor(id);

            Blend originalTint = assertInstanceOf(Blend.class, node.getEffect(), "sanity check: this button should start tinted");

            fire(node.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, node.getLayoutX(), node.getLayoutY());
            DropShadow glow = assertInstanceOf(DropShadow.class, node.getEffect(), "selecting should show the glow");
            assertSame(originalTint, glow.getInput(), "the glow should wrap the exact same tint, not discard it");

            controller.setSize(id, 45, 30);
            assertInstanceOf(DropShadow.class, node.getEffect(), "resizing while selected shouldn't disturb the glow/tint composition");

            viewModel.selectedWidgetIdState().set(null);
            assertSame(originalTint, node.getEffect(), "deselecting should restore the exact same tint, not null");
        });
    }

    @Test
    void selectingAnUntintedButtonStillWorksNormally() throws Exception {
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
            Node node = controller.nodeFor(id);

            assertNull(node.getEffect(), "sanity check: an untinted button starts with no effect");

            fire(node.getOnMousePressed(), MouseEvent.MOUSE_PRESSED, node.getLayoutX(), node.getLayoutY());
            DropShadow glow = assertInstanceOf(DropShadow.class, node.getEffect());
            assertNull(glow.getInput(), "nothing to wrap - the glow's input should stay unset");

            viewModel.selectedWidgetIdState().set(null);
            assertNull(node.getEffect(), "deselecting an untinted button should go back to no effect at all");
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
