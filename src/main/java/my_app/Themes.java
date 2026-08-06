package my_app;

import javafx.scene.paint.Color;
import megalodonte.base.theme.*;

/**
 * Every color in the app lives here — either through {@link #light}/{@link #dark}
 * (the framework's {@link ThemeInterface} contract, driving every {@code Props}
 * -based component via {@link ThemeManager}) or, for the handful of canvas/builder
 * concepts the generic {@link ThemeColors} record has no slot for (selection glow,
 * alignment guides, grid overlay), a dedicated constant below.
 * <p>
 * Note: {@link ThemeManager}-driven styling calls {@code theme.colors()}
 * unconditionally as soon as any component sets a color-ish prop (see
 * {@code StyleUtils.getFinalBackgroundColor}), even when that prop is a literal
 * that never falls back to the theme — so {@link #colors()} (etc.) returning
 * {@code null}, as this class did before, crashes with an NPE the moment any such
 * component renders. Real values are required, not just nice to have.
 */
public class Themes {

    public static final ThemeInterface light = new ThemeInterface() {
        @Override
        public ThemeColors colors() {
            return new ThemeColors(
                    "#f8fafc",   // background
                    "#ffffff",   // surface
                    "#2563eb",   // primary
                    "#64748b",   // secondary
                    "#1e293b",   // textPrimary
                    "#64748b",   // textSecondary
                    "transparent", // border
                    "#A9A9A9",   // placeholder
                    "#dbeafe",   // selection
                    "#93c5fd",   // focusRing
                    "#f1f5f9"    // hover
            );
        }

        @Override
        public ThemeTypography typography() {
            return new ThemeTypography(18, 16, 14, 12);
        }

        @Override
        public ThemeSpacing spacing() {
            return new ThemeSpacing(4, 8, 16, 24, 32);
        }

        @Override
        public ThemeBorder border() {
            return new ThemeBorder(0, 4, 6, 8);
        }
    };

    public static final ThemeInterface dark = new ThemeInterface() {
        @Override
        public ThemeColors colors() {
            return new ThemeColors(
                    "#0f172a",   // background
                    "#1e293b",   // surface
                    "#3b82f6",   // primary
                    "#94a3b8",   // secondary
                    "#f1f5f9",   // textPrimary
                    "#94a3b8",   // textSecondary
                    "transparent", // border
                    "#64748b",   // placeholder
                    "#1e3a8a",   // selection
                    "#60a5fa",   // focusRing
                    "#334155"    // hover
            );
        }

        @Override
        public ThemeTypography typography() {
            return new ThemeTypography(18, 16, 14, 12);
        }

        @Override
        public ThemeSpacing spacing() {
            return new ThemeSpacing(4, 8, 16, 24, 32);
        }

        @Override
        public ThemeBorder border() {
            return new ThemeBorder(0, 4, 6, 8);
        }
    };

    // --- Canvas/builder-specific colors - no slot for these in ThemeColors ---
    //
    // The Canva's own background (before a reference image is loaded) and the
    // status bar's background reuse theme.colors().surface()/.hover() directly
    // at their call sites in HomeScreen (both are plain reads, safe regardless
    // of theme) - CANVAS_BORDER_COLOR stays a dedicated constant instead of
    // reusing theme.colors().border(), since that field is also the app-wide
    // fallback for every other component's border (StyleUtils.getFinalBorderColor)
    // and is deliberately "transparent" in both themes - changing it here would
    // put a visible border on every unrelated component that doesn't set its own.

    /** The Canva's visible boundary, marking the target screen/HUD bounds. */
    public static final String CANVAS_BORDER_COLOR = "#999999";

    /** Feedback text color for load/save/export failures. */
    public static final String ERROR_TEXT_COLOR = "#ff6b6b";

    /** Glow applied to the currently-selected widget on the Canva. */
    public static final Color SELECTION_HIGHLIGHT_COLOR = Color.DODGERBLUE;

    /** Center-alignment guide lines shown while dragging a widget on the Canva. */
    public static final Color GUIDE_COLOR = Color.web("#ff4081");

    /**
     * Faint grid overlay drawn on the Canva when the "Grid" checkbox is on —
     * separate light/dark variants (dark black-on-near-black at the same low
     * opacity used for light mode was reported as basically invisible; white
     * needs the opacity itself a bit higher too, since a bright color read as
     * faint against a light background reads as barely-there against a dark
     * one at the exact same alpha).
     */
    public static final Color GRID_COLOR_LIGHT = Color.rgb(0, 0, 0, 0.08);
    public static final Color GRID_COLOR_DARK = Color.rgb(255, 255, 255, 0.14);
}
