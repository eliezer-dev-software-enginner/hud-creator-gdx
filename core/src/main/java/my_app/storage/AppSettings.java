package my_app.storage;

/**
 * The builder's own preferences — not a UI layout — persisted by
 * {@link AppStorage} across runs. {@code lastLayoutFile}, when present, is
 * reopened automatically on the next launch instead of starting from a
 * blank canvas. {@code lastSkinDirectory}, when present, is where "Carregar
 * Skin" opens its file chooser instead of the bundled example assets.
 */
public record AppSettings(boolean showingGrid, String lastLayoutFile, boolean isLightTheme, String lastSkinDirectory) {

    public static AppSettings defaults() {
        return new AppSettings(true, null, true, null);
    }
}
