package my_app;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.image.Image;
import my_app.skin.AtlasRegion;
import my_app.skin.SkinLoader;
import my_app.skin.SkinModel;
import my_app.skin.render.DrawableView;
import my_app.skin.render.SkinImages;
import my_app.widget.WidgetSpec;
import my_app.widget.render.WidgetViews;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Diagnostic sweep across every bundled example skin under {@code gdx-skins}
 * (not just the handful individual tests happen to load) - exercises exactly
 * what the Palette itself does once a skin loads: build every declared
 * {@code Button}/{@code TextButton}/{@code Label} style, and every raw atlas
 * region. Written after finding a real gap this way (the {@code comic} skin's
 * {@code LabelStyle.background} never rendering) to catch any more of the
 * same *class* of bug - an optional style field or region convention this
 * project's parsers/renderers don't handle - before a user runs into it.
 */
class AllSkinsSmokeTest {

    private static final Path GDX_SKINS_DIR = Path.of("..", "gdx-skins");

    @BeforeAll
    static void initJavaFx() {
        new JFXPanel();
    }

    @Test
    void everyBundledSkinLoadsAndBuildsEveryDeclaredStyle() throws Exception {
        List<Path> skinJsonPaths = findSkinJsonFiles();
        assertTrue(skinJsonPaths.size() > 20, "sanity check - should have found most of gdx-skins' ~35 skins, found " + skinJsonPaths.size());

        List<String> failures = new ArrayList<>();
        List<String> fontFallbacks = new ArrayList<>();

        runOnFxThreadAndWait(() -> {
            for (Path skinJson : skinJsonPaths) {
                checkOneSkin(skinJson, failures, fontFallbacks);
            }
        });

        if (!fontFallbacks.isEmpty()) {
            System.out.println("Fonts that fell back to the plain system-font rendering (not a hard failure, but real glyphs didn't resolve):");
            fontFallbacks.forEach(f -> System.out.println("  " + f));
        }

        if (!failures.isEmpty()) {
            fail(failures.size() + " failure(s) across " + skinJsonPaths.size() + " skins:\n"
                    + String.join("\n", failures));
        }
    }

    private void checkOneSkin(Path skinJson, List<String> failures, List<String> fontFallbacks) {
        String skinLabel = GDX_SKINS_DIR.relativize(skinJson).toString();
        SkinModel skin;
        Image atlasImage;
        try {
            skin = SkinLoader.load(skinJson);
            atlasImage = SkinImages.loadAtlasImage(skin);
        } catch (Exception e) {
            failures.add(skinLabel + ": failed to load - " + e);
            return;
        }

        for (String fontName : skin.fontNames()) {
            if (skin.font(fontName).isEmpty()) {
                fontFallbacks.add(skinLabel + ": font \"" + fontName + "\"");
            }
        }

        for (String styleName : skin.styleNames("ButtonStyle")) {
            build(skin, atlasImage, new WidgetSpec.ButtonSpec(styleName), skinLabel, "ButtonStyle." + styleName, failures);
        }
        for (String styleName : skin.styleNames("TextButtonStyle")) {
            build(skin, atlasImage, new WidgetSpec.TextButtonSpec(styleName, "Test"), skinLabel, "TextButtonStyle." + styleName, failures);
        }
        for (String styleName : skin.styleNames("LabelStyle")) {
            build(skin, atlasImage, new WidgetSpec.LabelSpec(styleName, "Test"), skinLabel, "LabelStyle." + styleName, failures);
        }

        for (AtlasRegion region : skin.regions()) {
            try {
                SkinModel.ResolvedDrawable drawable = skin.drawable(region.name()).orElseThrow();
                DrawableView.of(atlasImage, drawable.region(), drawable.tint());
            } catch (Exception e) {
                failures.add(skinLabel + ": region \"" + region.name() + "\" - " + e);
            }
        }
    }

    private void build(SkinModel skin, Image atlasImage, WidgetSpec spec, String skinLabel, String styleLabel, List<String> failures) {
        try {
            WidgetViews.build(skin, atlasImage, spec);
        } catch (Exception e) {
            failures.add(skinLabel + ": " + styleLabel + " - " + e);
        }
    }

    /** Every {@code *.json} skin definition under {@code gdx-skins} - identified by declaring a {@code BitmapFont} section, so stray non-skin JSON (if any) doesn't get swept in as a false failure. */
    private static List<Path> findSkinJsonFiles() throws Exception {
        try (Stream<Path> walk = Files.walk(GDX_SKINS_DIR)) {
            return walk
                    .filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.toString().contains("/raw/"))
                    .filter(AllSkinsSmokeTest::looksLikeASkin)
                    .sorted()
                    .toList();
        }
    }

    private static boolean looksLikeASkin(Path jsonPath) {
        try {
            return Files.readString(jsonPath).contains("com.badlogic.gdx.graphics.g2d.BitmapFont");
        } catch (Exception e) {
            return false;
        }
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
