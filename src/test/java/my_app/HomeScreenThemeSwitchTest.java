package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import megalodonte.base.theme.ThemeManager;
import my_app.skin.SkinLoader;
import my_app.widget.WidgetSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Switching theme (see {@code Main}) rebuilds the whole screen via a fresh
 * {@code new HomeScreen(viewModel, theme).render()}, swapped in through
 * {@code Context.updateView} — which reuses the same {@link HomeScreenViewModel}
 * but, deliberately, skips {@code onMount()} (avoids re-triggering the
 * auto-reopen-last-layout side effect on every toggle). Each {@code render()}
 * builds a brand-new {@code Canva}/{@code CanvasController}, so anything
 * placed on the old one used to simply not exist on the new one - the model
 * ({@link HomeScreenViewModel#placedWidgets()}) survived, but nothing ever
 * replayed it onto the new Canva. Fixed in {@code HomeScreen.render()} by
 * re-placing whatever's already in {@code placedWidgets()} on every render.
 */
class HomeScreenThemeSwitchTest {

    private static final Path EXAMPLE_SKIN = Path.of(
            "source.images.and.assets", "extract to your assets folder", "skin.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void widgetsSurviveARebuildOfTheWholeScreen() throws Exception {
        runOnFxThreadAndWait(() -> {
            ThemeManager.setTheme(Themes.light); // v2.Scroll (built inside SkinPalettePanel, part of HomeScreen.render()) reads this directly

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.canvasWidthState().set(400);
            viewModel.canvasHeightState().set(300);
            viewModel.skinState().set(SkinLoader.load(EXAMPLE_SKIN));

            new HomeScreen(viewModel, Themes.light).render();
            CanvasController firstController = viewModel.canvasController();
            firstController.place(new WidgetSpec.ButtonSpec("default"), 60, 40);

            String placedId = viewModel.placedWidgets().get().get(0).id();
            assertNotNull(firstController.nodeFor(placedId), "sanity check: widget was actually placed");

            // Simulates the theme toggle: Main builds a whole new HomeScreen against
            // the same viewModel and swaps it in via Context.updateView, which skips
            // onMount() - so this is the only thing standing between the widget and
            // being silently dropped.
            new HomeScreen(viewModel, Themes.dark).render();
            CanvasController secondController = viewModel.canvasController();

            assertNotSame(firstController, secondController, "sanity check: render() really did build a new CanvasController");
            assertEquals(1, viewModel.placedWidgets().get().size(), "the widget should still be in the model");
            assertNotNull(secondController.nodeFor(placedId), "the widget should have been re-placed onto the new Canva");
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
