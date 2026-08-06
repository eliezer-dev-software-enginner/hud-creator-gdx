package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
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

/**
 * {@code TextButtonSpec}/{@code LabelSpec}'s text used to be fixed at drop
 * time (whatever the palette catalog defaulted to) — {@link CanvasController#setText}
 * is the "Properties" panel's "Text:" field's write path, mirroring the
 * existing {@link CanvasController#setNickname}. Verifies both halves of the
 * fix: the stored {@link my_app.widget.PlacedWidget}'s spec, and the actual
 * on-screen {@link Text} node - a bug in either one alone would still show
 * up as "editing text doesn't work" to the user.
 */
class CanvasControllerTextEditTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");
    // The bundled example skin has no LabelStyle at all - flat-earth (used elsewhere
    // this session for the TintedDrawable fix) does.
    private static final Path LABEL_CAPABLE_SKIN = Path.of(
            "..", "gdx-skins", "flat-earth", "skin", "flat-earth-ui.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void setTextUpdatesATextButtonsModelAndLiveNode() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            controller.setText(id, "Jogar");

            var spec = (WidgetSpec.TextButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals("Jogar", spec.text());
            assertEquals("default", spec.styleName(), "styleName shouldn't change");

            Node node = controller.nodeFor(id);
            Text textNode = (Text) ((StackPane) node).getChildren().get(1);
            assertEquals("Jogar", textNode.getText());
        });
    }

    @Test
    void setTextUpdatesALabelsModelAndLiveNode() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(LABEL_CAPABLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.LabelSpec("default", "Lives"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            controller.setText(id, "Vidas");

            var spec = (WidgetSpec.LabelSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals("Vidas", spec.text());

            Node node = controller.nodeFor(id);
            Text textNode = (Text) ((StackPane) node).getChildren().get(0);
            assertEquals("Vidas", textNode.getText());
        });
    }

    @Test
    void setTextIsANoOpForWidgetKindsWithoutText() throws Exception {
        runOnFxThreadAndWait(() -> {
            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));
            CanvasController controller = new CanvasController(new Canva(), viewModel);
            viewModel.attachCanvasController(controller);

            controller.place(new WidgetSpec.ButtonSpec("default"), 60, 40);
            String id = viewModel.placedWidgets().get().get(0).id();

            controller.setText(id, "shouldn't do anything");

            var spec = (WidgetSpec.ButtonSpec) viewModel.placedWidgets().get().get(0).spec();
            assertEquals("default", spec.styleName());
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
