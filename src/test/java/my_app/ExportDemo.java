package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.layout_components.Canva;
import megalodonte.theme.DefaultTheme;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Manual, end-to-end demonstration of Fase 5 — not a JUnit test. Places a
 * couple of widgets and calls {@link HomeScreenViewModel#exportTo} for real,
 * against the actual sibling {@code libgdx-example-game} project, so the
 * result can be inspected directly instead of just trusted from unit tests.
 * <p>
 * Run via the {@code exportDemo} Gradle task; look at
 * {@code ../libgdx-example-game/assets/ui/hud-demo.json} and the
 * {@code skin/} folder copied alongside it.
 */
public class ExportDemo {

    public static void main(String[] args) throws Exception {
        new JFXPanel();

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                ThemeManager.setTheme(new DefaultTheme());
                run();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void run() {
        HomeScreenViewModel viewModel = new HomeScreenViewModel();
        viewModel.canvasWidthState().set(640);
        viewModel.canvasHeightState().set(360);
        viewModel.skinState().set(SkinLoader.load(Path.of(
                "source.images.and.assets", "extract to your assets folder", "skin.json")));

        Canva canva = new Canva();
        var pane = (javafx.scene.layout.Region) canva.getNode();
        pane.setMinSize(640, 360);
        pane.setPrefSize(640, 360);
        pane.setMaxSize(640, 360);

        CanvasController controller = new CanvasController(canva, viewModel);
        controller.place(new WidgetSpec.TextButtonSpec("default", "Play"), 320, 180);
        controller.place(new WidgetSpec.ButtonSpec("toggle"), 100, 60);
        controller.place(new WidgetSpec.ImageSpec("icon-volume-up"), 600, 30);

        // Nickname the "Play" button so the game side can look it up and attach a real click listener.
        String playId = viewModel.placedWidgets().get().get(0).id();
        controller.setNickname(playId, "play");

        Path outputFile = Path.of("..", "libgdx-example-game", "assets", "ui", "hud-demo.json");
        viewModel.exportTo(outputFile);

        System.out.println(viewModel.statusMessageState().get());
    }
}
