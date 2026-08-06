package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Parent;
import javafx.scene.Scene;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.layout_components.Canva;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Palette sidebar (Properties + every catalog section + the region
 * list) used to only be scrollable through the region list's own inner
 * {@code v2.Scroll} — once loaded, everything above it (Properties, Buttons,
 * TextButtons, Labels) could get pushed out of view with no way to reach it
 * if the sidebar's actual window height was smaller than the total content.
 * {@link SkinPalettePanel} now wraps its whole catalog area in one outer
 * {@code v2.Scroll} too. Verified the only way that's actually meaningful:
 * force a real layout pass in a deliberately short window and confirm the
 * rendered content area ends up *shorter* than what the un-scrolled content
 * would need — i.e. it's actually being clipped/scrolled, not just quietly
 * growing the whole window past the available space (which would look fine
 * in isolation but is exactly the bug this is meant to fix).
 */
class SkinPalettePanelScrollTest {

    // flat-earth has ButtonStyle/TextButtonStyle/LabelStyle *and* a couple
    // dozen atlas regions - definitely taller than a 200px test window.
    private static final Path SKIN_WITH_LOTS_OF_CONTENT = Path.of(
            "..", "gdx-skins", "flat-earth", "skin", "flat-earth-ui.json");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void theWholePaletteScrollsInsteadOfJustGrowingPastTheWindow() throws Exception {
        runOnFxThreadAndWait(() -> {
            ThemeManager.setTheme(Themes.light);

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            viewModel.skinState().set(SkinLoader.load(SKIN_WITH_LOTS_OF_CONTENT));

            CanvasController controller = new CanvasController(new Canva(), viewModel);
            SkinPalettePanel panel = new SkinPalettePanel(viewModel, controller, Themes.light);
            Parent paletteNode = (Parent) panel.asComponent().getNode();

            double windowHeight = 200;
            new Scene(paletteNode, 260, windowHeight);
            paletteNode.applyCss();
            paletteNode.layout();

            // The Scroll's own outer node deliberately never grows to fit its content
            // (that's the entire point of it) - so "how tall would this be unscrolled"
            // has to be measured on the actual content node directly, bypassing the Scroll.
            double fullUnscrolledContentHeight = panel.catalogArea().getNode().prefHeight(-1);
            assertTrue(fullUnscrolledContentHeight > windowHeight,
                    "sanity check: this skin's full catalog should genuinely be taller than " + windowHeight
                            + "px unscrolled (was " + fullUnscrolledContentHeight + "px) - otherwise this test proves nothing");

            double actualRenderedHeight = panel.catalogScroll().getNode().getBoundsInParent().getHeight();
            assertTrue(actualRenderedHeight <= windowHeight + 1,
                    "the Scroll wrapping the Palette should stay within the window (" + windowHeight
                            + "px) and let its content scroll internally, not grow past it - actual height was " + actualRenderedHeight);
        });
    }

    @Test
    void regionsBelowTheOtherCatalogSectionsAreNotCollapsedToASliver() throws Exception {
        runOnFxThreadAndWait(() -> {
            ThemeManager.setTheme(Themes.light);

            HomeScreenViewModel viewModel = new HomeScreenViewModel();
            SkinModel skin = SkinLoader.load(SKIN_WITH_LOTS_OF_CONTENT);
            viewModel.skinState().set(skin);

            CanvasController controller = new CanvasController(new Canva(), viewModel);
            SkinPalettePanel panel = new SkinPalettePanel(viewModel, controller, Themes.light);
            Parent paletteNode = (Parent) panel.asComponent().getNode();

            new Scene(paletteNode, 260, 200);
            paletteNode.applyCss();
            paletteNode.layout();

            // buildRegionList() used to wrap the region list in its own nested Scroll,
            // which collapsed to ~0 height once the *outer* Scroll (wrapping the whole
            // Palette) started resizing catalogArea to exactly its preferred height,
            // leaving no leftover VBox space for the inner Scroll's Vgrow.ALWAYS to
            // claim - "um pontinho" instead of the actual region rows.
            double regionListHeight = panel.regionListNode().prefHeight(-1);
            double oneRowRoughHeight = 30; // generous lower bound - real rows are icon + text, ~40px
            int regionCount = skin.regions().size();
            assertTrue(regionListHeight > oneRowRoughHeight,
                    "region list should be tall enough to actually show its " + regionCount
                            + " rows, not collapsed to a sliver (was " + regionListHeight + "px)");
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
