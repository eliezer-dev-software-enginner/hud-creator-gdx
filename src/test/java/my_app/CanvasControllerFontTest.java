package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.paint.Color;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.skin.render.BitmapTextView;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Font color used to be fixed at whatever the skin style resolved to, with no
 * way to change it after placement. {@link CanvasController#setFontColor} is
 * the "Properties" panel's "Font:" row's write path, mirroring
 * {@link CanvasController#setText} — verifies both halves again: the stored
 * spec, and the actual live {@link BitmapTextView}.
 */
class CanvasControllerFontTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void setFontColorOverridesTheSkinsOwnColor() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            controller.setFontColor(id, "#ff0000");

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals("#ff0000", spec.fontColor());

            BitmapTextView textView = controller.textViewFor(id);
            assertEquals(Color.web("#ff0000"), textView.color());
        });
    }

    @Test
    void clearingTheFontColorRevertsToTheSkinsOwnColor() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            BitmapTextView textView = controller.textViewFor(id);
            Color skinDefault = textView.color();

            controller.setFontColor(id, "#ff0000");
            controller.setFontColor(id, null);

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertNull(spec.fontColor());
            assertEquals(skinDefault, textView.color());
        });
    }

    @Test
    void invalidFontColorIsRejected() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            controller.setFontColor(id, "not-a-color");

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertNull(spec.fontColor(), "invalid hex shouldn't be stored");
        });
    }

    @Test
    void editingTextDoesNotResetAPreviousFontOverride() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            controller.setFontColor(id, "#ff0000");
            controller.setText(id, "Jogar");

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals("Jogar", spec.text());
            assertEquals("#ff0000", spec.fontColor(), "editing text shouldn't clear a previously-set font color");
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
