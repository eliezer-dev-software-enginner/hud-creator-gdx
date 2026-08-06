package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
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

/**
 * Font color/size used to be fixed at whatever the skin style (color) or
 * JavaFX default (size - the skin has no size concept at all for a preview
 * Text) resolved to, with no way to change either after placement.
 * {@link CanvasController#setFontColor}/{@link CanvasController#setFontScale}
 * are the "Properties" panel's "Font:" row's write path, mirroring
 * {@link CanvasController#setText} — verifies both halves again: the stored
 * spec, and the actual live {@link Text} node.
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

            Text textNode = (Text) controller.textNodeFor(id);
            assertEquals(Color.web("#ff0000"), textNode.getFill());
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
            Text textNode = (Text) controller.textNodeFor(id);
            Color skinDefault = textNode.getFill() instanceof Color c ? c : null;

            controller.setFontColor(id, "#ff0000");
            controller.setFontColor(id, null);

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertNull(spec.fontColor());
            assertEquals(skinDefault, textNode.getFill());
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
    void setFontScaleResizesTheLiveTextRelativeToTheOriginalSize() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Text textNode = (Text) controller.textNodeFor(id);
            double baseSize = textNode.getFont().getSize();

            controller.setFontScale(id, 2.0);
            assertEquals(baseSize * 2, textNode.getFont().getSize(), 0.01);

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals(2.0, spec.fontScale());
        });
    }

    @Test
    void repeatedScaleEditsDontCompound() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Text textNode = (Text) controller.textNodeFor(id);
            double baseSize = textNode.getFont().getSize();

            controller.setFontScale(id, 2.0);
            controller.setFontScale(id, 1.5);

            // Must be 1.5x the ORIGINAL size, not 1.5x the already-doubled size (2.25x).
            assertEquals(baseSize * 1.5, textNode.getFont().getSize(), 0.01);
        });
    }

    @Test
    void clearingTheFontScaleRevertsToTheOriginalSize() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();
            Text textNode = (Text) controller.textNodeFor(id);
            double baseSize = textNode.getFont().getSize();

            controller.setFontScale(id, 2.0);
            controller.setFontScale(id, null);

            assertEquals(baseSize, textNode.getFont().getSize(), 0.01);
            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertNull(spec.fontScale());
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
            controller.setFontScale(id, 2.0);
            controller.setText(id, "Jogar");

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals("Jogar", spec.text());
            assertEquals("#ff0000", spec.fontColor(), "editing text shouldn't clear a previously-set font color");
            assertEquals(2.0, spec.fontScale(), "editing text shouldn't clear a previously-set font scale");
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
