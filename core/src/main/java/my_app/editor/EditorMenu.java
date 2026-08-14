package my_app.editor;

import com.badlogic.gdx.Gdx;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.type.ImInt;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The in-canvas main menu bar. Mirrors scene-game-2d-editor's
 * {@code EditorUI}: {@code JFileChooser} blocks its calling thread, so
 * every dialog runs on its own background thread to avoid freezing the
 * GLFW loop; GL-touching follow-up work is handed back to the render
 * thread via {@code Gdx.app.postRunnable}.
 */
public final class EditorMenu {

    private final HudEditorScreen screen;
    private volatile boolean dialogOpen;
    private String statusMessage = "No skin loaded. Use File > Load Skin...";

    /** The file "Save Layout" writes straight to without a dialog, once known — set by Open/Save-with-dialog/Export. */
    private Path currentLayoutFile;

    private boolean showGrid;
    private boolean darkTheme;
    private boolean resizePopupRequested;
    private final ImInt resizeWidth = new ImInt();
    private final ImInt resizeHeight = new ImInt();

    /** Set whenever Save/Export finishes (success or failure) — the inline {@code statusMessage} text alone is easy to miss for something as consequential as "did my export actually write a file", so this also pops up an impossible-to-miss modal. Null means no popup pending. */
    private String resultPopupMessage;
    private boolean resultPopupRequested;

    public EditorMenu(HudEditorScreen screen) {
        this.screen = screen;
        AppSettings settings = AppStorage.load();
        this.showGrid = settings.showingGrid();
        this.darkTheme = !settings.isLightTheme();
    }

    /** Must run after {@code ImGui.createContext()} — style calls need a live context. */
    public void applyInitialTheme() {
        applyTheme();
    }

    public void setStatus(String message) {
        this.statusMessage = message;
    }

    /** Called once, from {@code HudEditorScreen.create()}, if a previous session's layout auto-reopened successfully. */
    public void notifyLastLayoutRestored(Path path) {
        currentLayoutFile = path;
        statusMessage = "Layout reopened: " + path.getFileName();
    }

    public void render() {
        if (ImGui.beginMainMenuBar()) {
            if (ImGui.beginMenu("File")) {
                if (ImGui.menuItem("Load Skin...")) loadSkin();
                if (ImGui.menuItem("New Layout")) {
                    screen.newLayout();
                    currentLayoutFile = null;
                    statusMessage = "New layout.";
                }
                if (ImGui.menuItem("Open Layout...")) openLayout();
                ImGui.separator();
                if (ImGui.menuItem("Save Layout", "Ctrl+S")) save();
                if (ImGui.menuItem("Save Layout As...")) saveAs();
                if (ImGui.menuItem("Export Layout... (with skin)")) export();
                ImGui.separator();
                if (ImGui.menuItem("Resize Canvas...")) requestResizePopup();
                if (ImGui.menuItem("Load Background Image...")) loadBackgroundImage();
                if (ImGui.menuItem("Remove Background Image")) {
                    screen.clearBackgroundImage();
                    statusMessage = "Background image removed.";
                }
                ImGui.endMenu();
            }
            if (ImGui.beginMenu("View")) {
                if (ImGui.menuItem("Grid", "", showGrid)) {
                    showGrid = !showGrid;
                    screen.setGridVisible(showGrid);
                }
                if (ImGui.menuItem("Dark Theme", "", darkTheme)) {
                    darkTheme = !darkTheme;
                    applyTheme();
                    persistTheme();
                }
                ImGui.endMenu();
            }
            ImGui.text("   " + statusMessage);
            ImGui.endMainMenuBar();
        }
        renderResizePopup();
        renderResultPopup();
    }

    /** Queues {@link #resultPopupMessage} to open on the next {@link #render()} pass — ImGui requires {@code openPopup} to run inside the same frame's UI code, not from a background thread's callback. */
    private void showResultPopup(String message) {
        resultPopupMessage = message;
        resultPopupRequested = true;
    }

    private void renderResultPopup() {
        if (resultPopupRequested) {
            ImGui.openPopup("Result");
            resultPopupRequested = false;
        }
        ImGui.setNextWindowSize(360, 0, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("Result")) {
            ImGui.textWrapped(resultPopupMessage == null ? "" : resultPopupMessage);
            if (ImGui.button("OK")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void applyTheme() {
        if (darkTheme) {
            ImGui.styleColorsDark();
        } else {
            ImGui.styleColorsLight();
        }
        // ImGui's own styleColors* calls only recolor ImGui widgets (menu bar, Hierarchy/
        // Inspector) — the Scene2D-rendered chrome (canvas backdrop, status label) needs its
        // own theme-aware colors, which don't come from ImGui at all.
        screen.setDarkTheme(darkTheme);
    }

    private void persistTheme() {
        AppSettings s = AppStorage.load();
        AppStorage.save(new AppSettings(s.showingGrid(), s.lastLayoutFile(), !darkTheme, s.lastSkinDirectory()));
    }

    private void requestResizePopup() {
        resizeWidth.set((int) screen.canvasWidth());
        resizeHeight.set((int) screen.canvasHeight());
        resizePopupRequested = true;
    }

    private void renderResizePopup() {
        if (resizePopupRequested) {
            ImGui.openPopup("Resize Canvas");
            resizePopupRequested = false;
        }
        ImGui.setNextWindowSize(280, 130, ImGuiCond.Appearing);
        if (ImGui.beginPopupModal("Resize Canvas")) {
            ImGui.text("Between " + HudEditorScreen.MIN_CANVAS_DIMENSION + " and " + HudEditorScreen.MAX_CANVAS_DIMENSION + " px.");
            ImGui.inputInt("Width", resizeWidth);
            ImGui.inputInt("Height", resizeHeight);
            if (ImGui.button("OK")) {
                int w = HudEditorScreen.clampCanvasDimension(resizeWidth.get());
                int h = HudEditorScreen.clampCanvasDimension(resizeHeight.get());
                screen.resizeCanvas(w, h);
                statusMessage = "Canvas resized: " + w + "x" + h;
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel")) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    // ---- Swing file dialogs, off the render thread ----

    private void runDialog(Runnable dialogAndFollowUp) {
        if (dialogOpen) return;
        dialogOpen = true;
        Thread thread = new Thread(() -> {
            try {
                dialogAndFollowUp.run();
            } finally {
                dialogOpen = false;
            }
        }, "editor-file-dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadSkin() {
        runDialog(() -> {
            if (screen.hasSkinLoaded()) {
                int choice = JOptionPane.showConfirmDialog(null,
                        "A skin is already loaded. Replace it and clear the current canvas?",
                        "Replace skin", JOptionPane.YES_NO_OPTION);
                if (choice != JOptionPane.YES_OPTION) return;
            }

            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Skin JSON", "json"));
            preselect(chooser, AppStorage.load().lastSkinDirectory());
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

            Path path = chooser.getSelectedFile().toPath();
            Gdx.app.postRunnable(() -> {
                try {
                    screen.loadSkin(path);
                    currentLayoutFile = null;
                    statusMessage = "Skin loaded: " + path.getFileName();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    statusMessage = "Error: " + e.getMessage();
                }
            });
        });
    }

    private void openLayout() {
        runDialog(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Layout JSON", "json"));
            preselect(chooser, lastLayoutDirectory());
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

            Path path = chooser.getSelectedFile().toPath();
            Gdx.app.postRunnable(() -> {
                try {
                    screen.openLayout(path);
                    currentLayoutFile = path;
                    statusMessage = "Layout opened: " + path.getFileName();
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                    statusMessage = "Error: " + e.getMessage();
                }
            });
        });
    }

    /** Wired to Ctrl+S in {@link HudEditorScreen}'s key handler — same action as the "Save Layout" menu item. */
    public void saveShortcut() {
        save();
    }

    /** Overwrites the already-known file directly with no dialog; falls back to {@link #saveAs()} the first time. */
    private void save() {
        if (currentLayoutFile == null) {
            saveAs();
            return;
        }
        try {
            screen.saveLayoutInPlace(currentLayoutFile);
            statusMessage = "Layout saved: " + currentLayoutFile.getFileName();
            showResultPopup("Layout saved to:\n" + currentLayoutFile.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            statusMessage = "Error: " + e.getMessage();
            showResultPopup("Error saving:\n" + e);
        }
    }

    private void saveAs() {
        runDialog(() -> {
            Path path = chooseLayoutSaveTarget("Save Layout");
            if (path == null) return;
            Gdx.app.postRunnable(() -> {
                try {
                    screen.saveLayoutInPlace(path);
                    currentLayoutFile = path;
                    statusMessage = "Layout saved: " + path.getFileName();
                    showResultPopup("Layout saved to:\n" + path.toAbsolutePath());
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                    statusMessage = "Error: " + e.getMessage();
                    showResultPopup("Error saving:\n" + e);
                }
            });
        });
    }

    private void export() {
        runDialog(() -> {
            Path path = chooseLayoutSaveTarget("Export Layout");
            if (path == null) return;
            Gdx.app.postRunnable(() -> {
                try {
                    screen.saveLayout(path);
                    statusMessage = "Layout exported: " + path.getFileName();
                    showResultPopup("Layout exported to:\n" + path.toAbsolutePath());
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                    statusMessage = "Error: " + e.getMessage();
                    showResultPopup("Error exporting:\n" + e);
                }
            });
        });
    }

    /** Shared Save-As/Export file picker — returns null if cancelled. Runs on the calling (background dialog) thread. */
    private Path chooseLayoutSaveTarget(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Layout JSON", "json"));
        chooser.setSelectedFile(new File("layout.json"));
        preselect(chooser, lastLayoutDirectory());
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null;

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getParentFile(), file.getName() + ".json");
        }
        return file.toPath();
    }

    private void loadBackgroundImage() {
        runDialog(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"));
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;

            Path path = chooser.getSelectedFile().toPath();
            Gdx.app.postRunnable(() -> {
                try {
                    screen.loadBackgroundImage(path);
                    statusMessage = "Background image: " + path.getFileName();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    statusMessage = "Error: " + e.getMessage();
                }
            });
        });
    }

    private String lastLayoutDirectory() {
        String reference = currentLayoutFile != null ? currentLayoutFile.toString() : AppStorage.load().lastLayoutFile();
        if (reference == null) return null;
        File parent = new File(reference).getParentFile();
        return parent != null ? parent.getPath() : null;
    }

    private static void preselect(JFileChooser chooser, String directoryPath) {
        if (directoryPath == null) return;
        File dir = new File(directoryPath);
        if (dir.isDirectory()) {
            chooser.setCurrentDirectory(dir);
        }
    }
}
