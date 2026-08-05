package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code handleNew()} used to be an empty stub — clicking "New" in the menu
 * visibly did nothing. Verifies it actually resets the project: canvas
 * cleared, skin/background/error forgotten, canvas size back to default,
 * widget id counter restarted. Runs on the real JavaFX Application Thread
 * since {@code CanvasController.clear()} touches a real {@link Canva}.
 */
class HomeScreenViewModelNewTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void newResetsTheWholeProject() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            Canva canva = new Canva();
            CanvasController controller = new CanvasController(canva, viewModel);
            viewModel.attachCanvasController(controller);

            // Get the project into a non-default state first.
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            viewModel.canvasWidthState().set(999);
            viewModel.canvasHeightState().set(888);
            viewModel.backgroundImagePathState().set(EXAMPLE_SKIN.toAbsolutePath().toString());
            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String placedId = viewModel.placedWidgets().get().get(0).id();
            viewModel.selectedWidgetIdState().set(placedId);

            assertEquals(1, viewModel.placedWidgets().get().size(), "sanity check: widget was actually placed");
            assertTrue(viewModel.nextWidgetId().startsWith("widget-2"), "sanity check: counter advanced past 1");

            viewModel.handleNew();

            assertNull(viewModel.skinState().get(), "skin should be forgotten");
            assertNull(viewModel.loadErrorState().get(), "load error should be forgotten");
            assertNull(viewModel.backgroundImagePathState().get(), "background image should be forgotten");
            assertEquals(640, viewModel.canvasWidthState().get(), "canvas width back to default");
            assertEquals(360, viewModel.canvasHeightState().get(), "canvas height back to default");
            assertEquals(0, viewModel.placedWidgets().get().size(), "canvas should be cleared");
            assertNull(controller.nodeFor(placedId), "the placed widget's node should be gone from the Canva too");
            assertNull(viewModel.selectedWidgetIdState().get(), "selection should be cleared");
            assertEquals("", viewModel.statusMessageState().get(), "status message should be cleared");
            assertEquals("widget-1", viewModel.nextWidgetId(), "widget id counter should restart from 1");
        });
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
