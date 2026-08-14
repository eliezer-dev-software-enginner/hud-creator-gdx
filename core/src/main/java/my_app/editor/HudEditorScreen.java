package my_app.editor;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import my_app.project.PlacedWidgetDto;
import my_app.project.SkinAssetExporter;
import my_app.project.UiLayout;
import my_app.project.UiLayoutAssembler;
import my_app.project.UiLayoutReader;
import my_app.project.UiLayoutWriter;
import my_app.storage.AppSettings;
import my_app.storage.AppStorage;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The whole editor: canvas + palette rendered normally through the Scene2D
 * {@link Stage}, with an ImGui menu bar/Hierarchy/Inspector overlaid on top
 * of the same GL context — the same single-process architecture
 * scene-game-2d-editor uses (ImGui for in-canvas chrome, {@code
 * JFileChooser} only for blocking native file dialogs run off this
 * thread — see {@link EditorMenu}). An earlier version of this app ran a
 * separate JavaFX process for the menu instead; that combination crashed
 * (JavaFX's GTK backend and libGDX's GLFW backend both touch Xlib, unsafely,
 * from separate threads) and was abandoned in favor of this.
 */
public class HudEditorScreen extends ApplicationAdapter {

    public static final int PALETTE_WIDTH = 260;
    public static final int INSPECTOR_WIDTH = 260;
    public static final int MARGIN = 20;
    public static final int DEFAULT_CANVAS_WIDTH = 800;
    public static final int DEFAULT_CANVAS_HEIGHT = 480;
    static final int MIN_CANVAS_DIMENSION = 16;
    static final int MAX_CANVAS_DIMENSION = 4096;
    private static final int MIN_WIDGET_SIZE = 8;
    private static final int HANDLE_SIZE = 10;
    private static final int GRID_SPACING = 32;
    private static final double PASTE_OFFSET = 20;
    private static final float GUIDE_SNAP_DISTANCE = 5f;
    private static final float GUIDE_SHOW_DISTANCE = 15f;

    // The ImGui theme toggle ("Dark Theme") only recolors ImGui's own widgets by default
    // (menu bar, Hierarchy/Inspector) — the Scene2D-rendered chrome (window backdrop, status
    // label) needs its own theme-aware colors, applied via setDarkTheme below.
    private static final Color DARK_BACKGROUND = new Color(0.16f, 0.16f, 0.16f, 1f);
    private static final Color LIGHT_BACKGROUND = new Color(0.94f, 0.95f, 0.96f, 1f);
    private static final Color DARK_STATUS_TEXT = Color.LIGHT_GRAY;
    private static final Color LIGHT_STATUS_TEXT = new Color(0.12f, 0.16f, 0.22f, 1f);

    private final AtomicInteger nextWidgetIndex = new AtomicInteger(1);

    private final ImGuiImplGlfw imGuiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();
    private final EditorMenu menu = new EditorMenu(this);
    private final HierarchyPanel hierarchyPanel = new HierarchyPanel();
    private final InspectorPanel inspectorPanel = new InspectorPanel();

    private Stage stage;
    private ShapeRenderer shapeRenderer;
    private BitmapFont fallbackFont;

    private Skin skin;
    private Path skinJsonPath;
    private ScrollPane paletteScroll;
    private DragAndDrop dragAndDrop;
    private CanvasPanel canvas;
    private Image backgroundImage;
    private Texture backgroundTexture;
    private String backgroundImagePath;
    private Label statusLabel;
    private final Set<Actor> selectedActors = new LinkedHashSet<>();
    private List<PlacedWidget> clipboard = new ArrayList<>();
    private Image resizeHandle;
    private Texture handleTexture;
    private boolean showGrid;
    private Float guideCanvasX;
    private Float guideCanvasY;
    private final Color backgroundClearColor = new Color(DARK_BACKGROUND);

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        shapeRenderer = new ShapeRenderer();
        fallbackFont = new BitmapFont();

        statusLabel = new Label("No skin loaded. Use the File menu > Load Skin...",
                new Label.LabelStyle(fallbackFont, Color.LIGHT_GRAY));
        // Without a wrap width, a long message just draws as one long line — since the canvas
        // sits to the right of the palette and is drawn after it (so its opaque checker
        // background paints over anything under it), an unwrapped line reads as "cut off"
        // exactly at the canvas' left edge instead of running past the window.
        statusLabel.setWrap(true);
        statusLabel.setWidth(PALETTE_WIDTH);
        statusLabel.setHeight(fallbackFont.getLineHeight() * 3);
        stage.addActor(statusLabel);

        canvas = new CanvasPanel(DEFAULT_CANVAS_WIDTH, DEFAULT_CANVAS_HEIGHT);
        stage.addActor(canvas);
        showGrid = AppStorage.load().showingGrid();

        handleTexture = solidTexture(Color.CYAN);
        resizeHandle = new Image(handleTexture);
        resizeHandle.setSize(HANDLE_SIZE, HANDLE_SIZE);
        resizeHandle.setVisible(false);
        canvas.addActor(resizeHandle);
        wireResizeHandle();

        canvas.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (event.getTarget() == canvas) {
                    clearSelection();
                }
                return false; // don't consume — let this bubble normally, it just observes clicks that hit the canvas itself
            }
        });

        layoutChrome();

        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                // ImGui and the Scene2D Stage share the same GLFW window/input backend, so this
                // listener sees every keystroke regardless of what currently has focus. Without
                // this guard, backspacing while renaming a widget in the Inspector's "Id" field
                // (Input.Keys.DEL is libGDX's name for Backspace, not the Delete key) also fired
                // deleteSelected() on the canvas — same gap would hit Ctrl+C/V/D/S while typing
                // into any ImGui field, not just this one.
                if (ImGui.getIO().getWantCaptureKeyboard()) {
                    return false;
                }
                // Esc while editing an ImGui field (e.g. the Inspector's Id field) is already
                // handled by ImGui itself before this listener even sees it — the guard above
                // returns first in that case. Esc reaching here means nothing has keyboard focus,
                // so it clears the canvas selection instead (hides the resize handle, Inspector
                // goes back to "No widget selected.").
                if (keycode == Input.Keys.ESCAPE) {
                    boolean hadSelection = !selectedActors.isEmpty();
                    clearSelection();
                    return hadSelection;
                }
                boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
                boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                if ((keycode == Input.Keys.FORWARD_DEL || keycode == Input.Keys.DEL) && !selectedActors.isEmpty()) {
                    deleteSelected();
                    return true;
                }
                if (ctrl && keycode == Input.Keys.C) {
                    copySelection();
                    return true;
                }
                if (ctrl && keycode == Input.Keys.V) {
                    pasteWidgets(clipboard);
                    return true;
                }
                if (ctrl && keycode == Input.Keys.D) {
                    duplicateSelection();
                    return true;
                }
                if (ctrl && keycode == Input.Keys.S) {
                    menu.saveShortcut();
                    return true;
                }
                // Checked before the plain Ctrl+G case below, since both share the same keycode.
                if (ctrl && shift && keycode == Input.Keys.G) {
                    ungroupSelection();
                    return true;
                }
                if (ctrl && keycode == Input.Keys.G) {
                    groupSelection();
                    return true;
                }
                return false;
            }
        });

        initImGui();
        menu.applyInitialTheme();

        Path restored = restoreLastLayout();
        if (restored != null) {
            menu.notifyLastLayoutRestored(restored);
        }
    }

    private void initImGui() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename("editor-layout.ini");
        io.getFonts().addFontDefault();
        io.getFonts().build();

        long windowHandle = ((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle();
        imGuiGlfw.init(windowHandle, true);
        imGuiGl3.init("#version 150");
    }

    private static Texture solidTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    /**
     * Positions chrome that doesn't participate in Scene2D's own layout
     * system — called on create, resize, and canvas resize. Anchored from
     * the top ({@code windowHeight - MARGIN} downward), not the bottom —
     * libGDX's Y grows upward from the bottom, so a bottom anchor
     * (the previous {@code canvas.setPosition(x, MARGIN)}) pushes any
     * leftover space (whenever the window is taller than the canvas) above
     * the canvas instead of below it, leaving a gap under the menu bar
     * that grows with the window. The palette only looked top-aligned
     * because its height, not just its position, already stretched to
     * fill the window.
     */
    private void layoutChrome() {
        // Rounded to whole pixels — ScreenViewport maps 1 world unit to ~1 screen pixel, so a
        // fractional position here (statusLabel.getHeight() depends on font metrics that aren't
        // whole numbers) puts the canvas at a sub-pixel offset, which is enough for Nearest
        // texture filtering to alias the checker/grid tiling inconsistently frame to frame.
        float top = Math.round(Gdx.graphics.getHeight() - MARGIN);
        statusLabel.setPosition(MARGIN, Math.round(top - statusLabel.getHeight()));
        canvas.setPosition(Math.round(PALETTE_WIDTH + MARGIN * 2f), Math.round(top - canvas.getHeight()));
        if (paletteScroll != null) {
            // Bottom-anchored at MARGIN, stopping *below* the status label (with its own gap)
            // instead of sharing its exact top — otherwise the palette's own top edge coincides
            // with the status label's, and since the palette is added to the stage after it, its
            // first row ("Regioes") draws right on top of the status text with no visible gap.
            float paletteTop = statusLabel.getY() - MARGIN;
            paletteScroll.setBounds(MARGIN, MARGIN, PALETTE_WIDTH, Math.round(paletteTop - MARGIN));
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        layoutChrome();
    }

    @Override
    public void render() {
        ScreenUtils.clear(backgroundClearColor.r, backgroundClearColor.g, backgroundClearColor.b, 1f);
        drawGrid();
        stage.act();
        AnchorResolver.resolve(canvas, actorsByIdForAnchors());
        stage.draw();
        drawSelectionHighlight();
        drawGuides();

        imGuiGlfw.newFrame();
        imGuiGl3.newFrame();
        ImGui.newFrame();

        menu.render();

        // Docks just past the canvas' own right edge — not the window's — so it tracks canvas
        // resizes and never overlaps the canvas itself, unlike an earlier version of this that
        // anchored off Gdx.graphics.getWidth() (which only applies once via FirstUseEver, so it
        // didn't even track a later window resize/maximize, let alone a canvas resize).
        float panelDockX = canvas.getX() + canvas.getWidth() + MARGIN;

        Actor primaryBefore = soleSelected();
        Actor hierarchyResult = hierarchyPanel.render(canvas, primaryBefore, panelDockX);
        selectedActors.removeIf(a -> a.getParent() == null); // rows can be removed straight from the panel
        if (hierarchyResult != primaryBefore) {
            if (hierarchyResult == null) {
                clearSelection();
            } else {
                replaceSelection(hierarchyResult);
            }
        } else {
            refreshSelectionVisuals();
        }
        inspectorPanel.render(selectedActors, canvas, skin, panelDockX);

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    /** Drawn before {@code stage.draw()} so placed widgets render on top of it, not the other way round. */
    private void drawGrid() {
        if (!showGrid) {
            return;
        }
        Vector2 origin = canvas.localToStageCoordinates(new Vector2(0, 0));
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.12f);
        for (float x = 0; x <= canvas.getWidth(); x += GRID_SPACING) {
            shapeRenderer.line(origin.x + x, origin.y, origin.x + x, origin.y + canvas.getHeight());
        }
        for (float y = 0; y <= canvas.getHeight(); y += GRID_SPACING) {
            shapeRenderer.line(origin.x, origin.y + y, origin.x + canvas.getWidth(), origin.y + y);
        }
        shapeRenderer.end();
    }

    private void drawSelectionHighlight() {
        if (selectedActors.isEmpty()) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.CYAN);
        for (Actor selected : selectedActors) {
            if (selected.getStage() == null) continue;
            Vector2 corner = selected.getParent()
                    .localToStageCoordinates(new Vector2(selected.getX(), selected.getY()));
            shapeRenderer.rect(corner.x, corner.y, selected.getWidth(), selected.getHeight());
        }
        shapeRenderer.end();
    }

    /** Vertical/horizontal magenta line(s) shown while dragging a single widget near an alignment target — see {@link #moveSingleWithGuides}. */
    private void drawGuides() {
        if (guideCanvasX == null && guideCanvasY == null) {
            return;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.MAGENTA);
        if (guideCanvasX != null) {
            Vector2 top = canvas.localToStageCoordinates(new Vector2(guideCanvasX, canvas.getHeight()));
            Vector2 bottom = canvas.localToStageCoordinates(new Vector2(guideCanvasX, 0));
            shapeRenderer.line(top.x, top.y, bottom.x, bottom.y);
        }
        if (guideCanvasY != null) {
            Vector2 left = canvas.localToStageCoordinates(new Vector2(0, guideCanvasY));
            Vector2 right = canvas.localToStageCoordinates(new Vector2(canvas.getWidth(), guideCanvasY));
            shapeRenderer.line(left.x, left.y, right.x, right.y);
        }
        shapeRenderer.end();
    }

    @Override
    public void dispose() {
        imGuiGl3.shutdown();
        imGuiGlfw.shutdown();
        ImGui.destroyContext();

        stage.dispose();
        shapeRenderer.dispose();
        fallbackFont.dispose();
        canvas.dispose();
        handleTexture.dispose();
        if (skin != null) {
            skin.dispose();
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
        }
    }

    // ---- operations invoked (directly, or via Gdx.app.postRunnable from EditorMenu's dialog threads) ----

    public boolean hasSkinLoaded() {
        return skin != null;
    }

    public void loadSkin(Path newSkinJsonPath) {
        Skin oldSkin = skin;
        skin = new Skin(new FileHandle(newSkinJsonPath.toFile()));
        skinJsonPath = newSkinJsonPath;
        if (oldSkin != null) {
            oldSkin.dispose();
        }
        clearCanvas();
        rebuildPalette();
        statusLabel.setText("Skin: " + newSkinJsonPath.getFileName());
        updateSettings(s -> new AppSettings(s.showingGrid(), s.lastLayoutFile(), s.isLightTheme(),
                newSkinJsonPath.toAbsolutePath().getParent().toString()));
    }

    public void newLayout() {
        clearCanvas();
    }

    public void openLayout(Path layoutFile) throws IOException {
        UiLayout layout = UiLayoutReader.read(layoutFile);
        Path skinPath = layoutFile.toAbsolutePath().normalize().getParent().resolve(layout.skinPath()).normalize();
        if (skin == null || !skinPath.equals(skinJsonPath)) {
            loadSkin(skinPath);
        } else {
            clearCanvas();
        }
        resizeCanvasInternal(layout.canvasWidth(), layout.canvasHeight());
        setBackgroundImage(layout.backgroundImagePath() == null ? null
                : layoutFile.toAbsolutePath().normalize().getParent().resolve(layout.backgroundImagePath()).toString());

        for (PlacedWidgetDto dto : layout.widgets()) {
            PlacedWidget placed = UiLayoutAssembler.fromDto(dto);
            placeExisting(placed);
        }

        rememberLastLayout(layoutFile);
    }

    /**
     * "Export": self-contained — copies the skin's own files alongside the
     * output so the result works even if the original skin moves. See
     * {@link #saveLayoutInPlace} for the lighter "Save" that references the
     * skin where it already is instead.
     */
    public void saveLayout(Path outputFile) throws IOException {
        if (skin == null || skinJsonPath == null) {
            throw new IllegalStateException("Load a skin before saving.");
        }
        List<PlacedWidget> placed = snapshotPlacedWidgets();
        Path outputDir = outputFile.toAbsolutePath().normalize().getParent();
        String copiedSkinName = SkinAssetExporter.copyInto(skinJsonPath, outputDir);
        String backgroundRef = copyBackgroundImageInto(outputDir);
        UiLayout layout = UiLayoutAssembler.assemble(copiedSkinName, (int) canvas.getWidth(), (int) canvas.getHeight(),
                backgroundRef, placed);
        UiLayoutWriter.write(layout, outputFile);
        rememberLastLayout(outputFile);
    }

    /** "Save": writes the layout referencing the skin where it already lives — no copying, cheaper for iterating on a layout in place. */
    public void saveLayoutInPlace(Path outputFile) throws IOException {
        if (skin == null || skinJsonPath == null) {
            throw new IllegalStateException("Load a skin before saving.");
        }
        List<PlacedWidget> placed = snapshotPlacedWidgets();
        String skinPathRef = UiLayoutAssembler.relativePath(skinJsonPath, outputFile);
        String backgroundRef = backgroundImagePath == null ? null
                : UiLayoutAssembler.relativePath(Path.of(backgroundImagePath), outputFile);
        UiLayout layout = UiLayoutAssembler.assemble(skinPathRef, (int) canvas.getWidth(), (int) canvas.getHeight(),
                backgroundRef, placed);
        UiLayoutWriter.write(layout, outputFile);
        rememberLastLayout(outputFile);
    }

    /** Copies the background reference image next to the exported skin, same as {@link SkinAssetExporter#copyInto} does for the skin, so "Export" stays self-contained. Returns the copied file's name, or {@code null} if no background is set. */
    private String copyBackgroundImageInto(Path outputDir) throws IOException {
        if (backgroundImagePath == null) {
            return null;
        }
        Path source = Path.of(backgroundImagePath);
        Files.copy(source, outputDir.resolve(source.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return source.getFileName().toString();
    }

    /** Resizes the canvas (and the OS window around it), keeping every placed widget's top-left/Y-down position relative to the canvas unchanged. */
    public void resizeCanvas(int newWidth, int newHeight) {
        int clampedWidth = clampCanvasDimension(newWidth);
        int clampedHeight = clampCanvasDimension(newHeight);
        resizeCanvasInternal(clampedWidth, clampedHeight);
        Gdx.graphics.setWindowedMode(
                PALETTE_WIDTH + MARGIN * 4 + clampedWidth + INSPECTOR_WIDTH,
                clampedHeight + MARGIN * 2);
    }

    static int clampCanvasDimension(int value) {
        return Math.max(MIN_CANVAS_DIMENSION, Math.min(MAX_CANVAS_DIMENSION, value));
    }

    public void setGridVisible(boolean visible) {
        showGrid = visible;
        updateSettings(s -> new AppSettings(visible, s.lastLayoutFile(), s.isLightTheme(), s.lastSkinDirectory()));
    }

    /** Recolors the Scene2D-rendered chrome (window backdrop, status label) to match — the ImGui theme toggle only recolors ImGui's own widgets on its own. */
    public void setDarkTheme(boolean dark) {
        backgroundClearColor.set(dark ? DARK_BACKGROUND : LIGHT_BACKGROUND);
        statusLabel.getStyle().fontColor.set(dark ? DARK_STATUS_TEXT : LIGHT_STATUS_TEXT);
    }

    /** Purely cosmetic reference image behind the canvas — ignored by whatever loads the exported layout in an actual game. */
    public void loadBackgroundImage(Path imagePath) {
        setBackgroundImage(imagePath.toAbsolutePath().toString());
    }

    public void clearBackgroundImage() {
        setBackgroundImage(null);
    }

    float canvasWidth() {
        return canvas.getWidth();
    }

    float canvasHeight() {
        return canvas.getHeight();
    }

    // ---- internals ----

    private void resizeCanvasInternal(int newWidth, int newHeight) {
        float oldHeight = canvas.getHeight();
        float newHeightF = newHeight;

        for (Actor child : canvas.getChildren()) {
            if (child == resizeHandle) {
                continue;
            }
            if (child == backgroundImage) {
                child.setSize(newWidth, newHeightF);
                continue;
            }
            // Re-derive each widget's top-left/Y-down offset from the OLD height, then reapply against the NEW one.
            double topLeftY = oldHeight - child.getY() - child.getHeight();
            child.setY(newHeightF - (float) topLeftY - child.getHeight());
        }

        canvas.setSize(newWidth, newHeightF);
        layoutChrome();
        updateHandlePosition();
    }

    private void rebuildPalette() {
        if (dragAndDrop != null) {
            dragAndDrop.clear();
        }
        if (paletteScroll != null) {
            paletteScroll.remove();
        }
        dragAndDrop = new DragAndDrop();
        Table paletteTable = PaletteBuilder.build(skin, dragAndDrop);
        paletteScroll = new ScrollPane(paletteTable);
        stage.addActor(paletteScroll);
        wirePaletteScrollFocus(paletteScroll);
        layoutChrome();

        dragAndDrop.addTarget(new DragAndDrop.Target(canvas) {
            @Override
            public boolean drag(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                return true;
            }

            @Override
            public void drop(DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                WidgetSpec spec = (WidgetSpec) payload.getObject();
                placeWidget(spec, x, y, null, null);
            }
        });
    }

    /**
     * Scene2D routes mouse-wheel events by "scroll focus," not by
     * hit-testing where the cursor is — unlike clicks, nothing receives a
     * scroll event unless something explicitly holds that focus. A plain
     * {@code ScrollPane} doesn't grab it just by existing or being hovered;
     * it only ends up focused as a side effect of being clicked/dragged,
     * which is why scrolling silently did nothing until the palette had
     * been dragged once. Claiming focus on hover (and releasing it on
     * exit, so it doesn't keep intercepting the wheel once the mouse moves
     * to the canvas or an ImGui panel) fixes it without needing any prior
     * interaction. {@code pointer == -1} distinguishes real mouse hover
     * from a touch-drag entering/leaving the actor's bounds.
     */
    private void wirePaletteScrollFocus(ScrollPane scrollPane) {
        scrollPane.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1) {
                    stage.setScrollFocus(scrollPane);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1 && stage.getScrollFocus() == scrollPane) {
                    stage.setScrollFocus(null);
                }
            }
        });
    }

    /** Also clears the background image (see {@link #setBackgroundImage}) — {@code canvas.clearChildren()} would otherwise orphan its actor/texture without disposing them. */
    private void clearCanvas() {
        canvas.clearChildren();
        canvas.addActor(resizeHandle);
        backgroundImage = null;
        clearSelection();
        nextWidgetIndex.set(1);
        setBackgroundImage(null);
    }

    private void setBackgroundImage(String path) {
        if (backgroundImage != null) {
            backgroundImage.remove();
            backgroundImage = null;
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
        backgroundImagePath = path;
        if (path != null) {
            backgroundTexture = new Texture(Gdx.files.absolute(path));
            backgroundImage = new Image(backgroundTexture);
            backgroundImage.setSize(canvas.getWidth(), canvas.getHeight());
            canvas.addActorAt(0, backgroundImage);
        }
    }

    /** Places a widget dropped from the palette; {@code localX}/{@code localY} are already canvas-local (bottom-left/Y-up). */
    private void placeWidget(WidgetSpec spec, float localX, float localY, Double width, Double height) {
        Actor actor = WidgetFactory.create(spec, skin);
        applySize(actor, width, height);
        // Center the widget under the drop point (matches the old app), clamped so it lands fully on the canvas.
        float x = Math.max(0, Math.min(canvas.getWidth() - actor.getWidth(), localX - actor.getWidth() / 2f));
        float y = Math.max(0, Math.min(canvas.getHeight() - actor.getHeight(), localY - actor.getHeight() / 2f));
        actor.setPosition(x, y);
        String id = "widget-" + nextWidgetIndex.getAndIncrement();
        actor.setName(id);
        actor.setUserObject(new PlacedMeta(id, spec, width, height));
        makeMovable(actor);
        canvas.addActor(actor);
        replaceSelection(actor);
    }

    /** Places a widget restored from a saved layout, whose x/y are top-left/Y-down and already known — an anchored widget's x/y get overwritten by {@link AnchorResolver} on the very next frame, this is just its fallback starting position. */
    private void placeExisting(PlacedWidget placed) {
        Actor actor = WidgetFactory.create(placed.spec(), skin);
        applySize(actor, placed.width(), placed.height());
        float stageY = canvas.getHeight() - (float) placed.y() - actor.getHeight();
        actor.setPosition((float) placed.x(), stageY);
        PlacedMeta meta = new PlacedMeta(placed.id(), placed.spec(), placed.width(), placed.height());
        meta.anchorBaseId = placed.anchorBaseId();
        if (placed.anchorAlignX() != null) meta.anchorAlignX = placed.anchorAlignX();
        if (placed.anchorAlignY() != null) meta.anchorAlignY = placed.anchorAlignY();
        if (placed.anchorOffsetX() != null) meta.anchorOffsetX = placed.anchorOffsetX();
        if (placed.anchorOffsetY() != null) meta.anchorOffsetY = placed.anchorOffsetY();
        meta.groupId = placed.groupId();
        actor.setUserObject(meta);
        actor.setName(placed.id());
        makeMovable(actor);
        canvas.addActor(actor);
        bumpNextWidgetIdPast(placed.id());
    }

    private void bumpNextWidgetIdPast(String id) {
        int index = indexSuffix(id);
        if (index >= nextWidgetIndex.get()) {
            nextWidgetIndex.set(index + 1);
        }
    }

    private static int indexSuffix(String id) {
        int dash = id.lastIndexOf('-');
        if (dash < 0) return 0;
        try {
            return Integer.parseInt(id.substring(dash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void applySize(Actor actor, Double width, Double height) {
        if (width != null && height != null) {
            actor.setSize(width.floatValue(), height.floatValue());
        } else if (actor instanceof Layout layout) {
            actor.setSize(layout.getPrefWidth(), layout.getPrefHeight());
        }
    }

    // ---- selection (single or multi — shift/ctrl+click toggles a widget into/out of the set) ----

    private static boolean isMultiSelectModifierDown() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    private void makeMovable(Actor actor) {
        actor.addListener(new InputListener() {
            private float lastX, lastY;
            private boolean dragged;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                boolean additive = isMultiSelectModifierDown();
                if (additive) {
                    if (!selectedActors.contains(actor)) {
                        addToSelection(actor);
                    }
                } else if (!selectedActors.contains(actor)) {
                    replaceSelection(actor);
                }
                dragged = false;
                lastX = x;
                lastY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                dragged = true;
                moveSelectionBy(x - lastX, y - lastY);
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!dragged && isMultiSelectModifierDown() && selectedActors.size() > 1 && selectedActors.contains(actor)) {
                    removeFromSelection(actor);
                }
                guideCanvasX = null;
                guideCanvasY = null;
            }
        });
    }

    /**
     * If exactly one actor is selected and it belongs to a Ctrl+G group
     * (see {@link PlacedMeta#groupId}), expands to every actor sharing
     * that group instead — used by drag/delete/copy/duplicate so clicking
     * any one member and acting on it acts on the whole group. Deliberately
     * NOT used for click-selection or the Inspector — those stay per-widget
     * even for a grouped one, so a single member's own properties (text,
     * anchor, id, …) are still directly editable without ungrouping first.
     * A real multi-selection (2+ actors already, from Shift/Ctrl-click)
     * passes through unchanged — group membership only kicks in for the
     * "clicked exactly one grouped widget" shorthand, not to silently pull
     * extra actors into a deliberate ad-hoc selection.
     */
    private Set<Actor> expandToGroup(Set<Actor> selection) {
        if (selection.size() != 1) {
            return selection;
        }
        Actor sole = selection.iterator().next();
        if (!(sole.getUserObject() instanceof PlacedMeta meta) || meta.groupId == null) {
            return selection;
        }
        Set<Actor> group = new LinkedHashSet<>();
        for (Actor child : canvas.getChildren()) {
            if (child.getUserObject() instanceof PlacedMeta m && meta.groupId.equals(m.groupId)) {
                group.add(child);
            }
        }
        return group;
    }

    /**
     * Single selection gets alignment guides ({@link #moveSingleWithGuides})
     * — matches the old app, where guides only applied to a lone widget's
     * drag. A group of 2+ moves together instead: same delta applied to
     * every member, clamped so the whole group's bounding box stays on the
     * canvas, then grid-snapped as one unit (no guides). "Group" here means
     * either an ad-hoc Shift/Ctrl-click multi-selection or a persistent
     * Ctrl+G group ({@link #expandToGroup}) — both end up moving the same way.
     */
    private void moveSelectionBy(float dx, float dy) {
        // Anchored widgets are positioned by AnchorResolver, not by dragging — reposition them
        // via the Inspector's offset fields instead. Filtered out here rather than blocked at
        // touchDown so a mixed selection still lets the free widgets in it move normally.
        List<Actor> movable = new ArrayList<>();
        for (Actor a : expandToGroup(selectedActors)) {
            if (!(a.getUserObject() instanceof PlacedMeta meta) || meta.anchorBaseId == null) {
                movable.add(a);
            }
        }
        if (movable.isEmpty()) {
            return;
        }
        if (movable.size() == 1) {
            moveSingleWithGuides(movable.get(0), dx, dy);
            return;
        }

        guideCanvasX = null;
        guideCanvasY = null;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Actor a : movable) {
            minX = Math.min(minX, a.getX());
            minY = Math.min(minY, a.getY());
            maxX = Math.max(maxX, a.getX() + a.getWidth());
            maxY = Math.max(maxY, a.getY() + a.getHeight());
        }

        if (minX + dx < 0) dx = -minX;
        if (maxX + dx > canvas.getWidth()) dx = canvas.getWidth() - maxX;
        if (minY + dy < 0) dy = -minY;
        if (maxY + dy > canvas.getHeight()) dy = canvas.getHeight() - maxY;

        if (showGrid) {
            float snappedMinX = Math.round((minX + dx) / (float) GRID_SPACING) * GRID_SPACING;
            float snappedMinY = Math.round((minY + dy) / (float) GRID_SPACING) * GRID_SPACING;
            dx = snappedMinX - minX;
            dy = snappedMinY - minY;
        }

        for (Actor a : movable) {
            a.moveBy(dx, dy);
        }
        updateHandlePosition();
    }

    /**
     * Snaps the dragged widget's left/center/right (X) and top/center/
     * bottom (Y) edges independently against the canvas center and every
     * other placed widget's matching edges: within {@link
     * #GUIDE_SNAP_DISTANCE} actually snaps and forces the position; within
     * the wider {@link #GUIDE_SHOW_DISTANCE} just shows the guide line
     * without moving anything. Grid-snap only kicks in on an axis that no
     * alignment guide already claimed this frame.
     */
    private void moveSingleWithGuides(Actor actor, float dx, float dy) {
        float w = actor.getWidth(), h = actor.getHeight();
        float newX = Math.max(0, Math.min(canvas.getWidth() - w, actor.getX() + dx));
        float newY = Math.max(0, Math.min(canvas.getHeight() - h, actor.getY() + dy));

        guideCanvasX = null;
        guideCanvasY = null;

        float[] xCandidates = {newX, newX + w / 2f, newX + w};
        float bestXDist = Float.MAX_VALUE, bestXTarget = 0;
        int bestXRole = -1;

        // Canvas centering only makes sense center-to-center — checking it against all three
        // roles (as the loop below does for other widgets) let the widget's LEFT edge "win" the
        // match whenever it happened to be numerically closer to the midline than the widget's
        // own center was, which is common (they cross the snap threshold at different drag
        // positions). Only role 1 (the widget's own center) is a legitimate canvas-center match.
        float canvasCenterDistX = Math.abs(xCandidates[1] - canvas.getWidth() / 2f);
        if (canvasCenterDistX < bestXDist) {
            bestXDist = canvasCenterDistX;
            bestXTarget = canvas.getWidth() / 2f;
            bestXRole = 1;
        }

        for (int role = 0; role < 3; role++) {
            float candidate = xCandidates[role];
            for (Actor other : canvas.getChildren()) {
                if (other == actor || !(other.getUserObject() instanceof PlacedMeta)) continue;
                for (float ox : new float[]{other.getX(), other.getX() + other.getWidth() / 2f, other.getX() + other.getWidth()}) {
                    float dist = Math.abs(candidate - ox);
                    if (dist < bestXDist) {
                        bestXDist = dist;
                        bestXTarget = ox;
                        bestXRole = role;
                    }
                }
            }
        }
        boolean snappedX = bestXDist <= GUIDE_SNAP_DISTANCE;
        if (snappedX) {
            newX += bestXTarget - xCandidates[bestXRole];
            guideCanvasX = bestXTarget;
        } else if (bestXDist <= GUIDE_SHOW_DISTANCE) {
            guideCanvasX = bestXTarget;
        }

        float[] yCandidates = {newY, newY + h / 2f, newY + h};
        float bestYDist = Float.MAX_VALUE, bestYTarget = 0;
        int bestYRole = -1;

        float canvasCenterDistY = Math.abs(yCandidates[1] - canvas.getHeight() / 2f);
        if (canvasCenterDistY < bestYDist) {
            bestYDist = canvasCenterDistY;
            bestYTarget = canvas.getHeight() / 2f;
            bestYRole = 1;
        }

        for (int role = 0; role < 3; role++) {
            float candidate = yCandidates[role];
            for (Actor other : canvas.getChildren()) {
                if (other == actor || !(other.getUserObject() instanceof PlacedMeta)) continue;
                for (float oy : new float[]{other.getY(), other.getY() + other.getHeight() / 2f, other.getY() + other.getHeight()}) {
                    float dist = Math.abs(candidate - oy);
                    if (dist < bestYDist) {
                        bestYDist = dist;
                        bestYTarget = oy;
                        bestYRole = role;
                    }
                }
            }
        }
        boolean snappedY = bestYDist <= GUIDE_SNAP_DISTANCE;
        if (snappedY) {
            newY += bestYTarget - yCandidates[bestYRole];
            guideCanvasY = bestYTarget;
        } else if (bestYDist <= GUIDE_SHOW_DISTANCE) {
            guideCanvasY = bestYTarget;
        }

        if (showGrid) {
            if (!snappedX) newX = Math.round(newX / (float) GRID_SPACING) * GRID_SPACING;
            if (!snappedY) newY = Math.round(newY / (float) GRID_SPACING) * GRID_SPACING;
        }

        actor.setPosition(newX, newY);
        updateHandlePosition();
    }

    /**
     * Deliberately does NOT call {@code actor.toFront()} — selection and
     * z-order are independent (matches the old app, whose
     * {@code CanvasController} never reordered a widget just because it got
     * selected). A newly *placed* widget still lands on top by construction
     * ({@code canvas.addActor} appends), but merely clicking/inspecting an
     * already-placed widget must not silently pull it above whatever was
     * layered on top of it since.
     */
    private void replaceSelection(Actor actor) {
        selectedActors.clear();
        selectedActors.add(actor);
        refreshSelectionVisuals();
    }

    private void addToSelection(Actor actor) {
        selectedActors.add(actor);
        refreshSelectionVisuals();
    }

    private void removeFromSelection(Actor actor) {
        selectedActors.remove(actor);
        refreshSelectionVisuals();
    }

    private void clearSelection() {
        selectedActors.clear();
        refreshSelectionVisuals();
    }

    private void refreshSelectionVisuals() {
        if (soleSelected() != null) {
            resizeHandle.setVisible(true);
            resizeHandle.toFront();
            updateHandlePosition();
        } else {
            resizeHandle.setVisible(false);
        }
    }

    /** Non-null only when exactly one widget is selected — the resize handle and Inspector's per-field editing both only make sense for a single widget. */
    private Actor soleSelected() {
        return selectedActors.size() == 1 ? selectedActors.iterator().next() : null;
    }

    private void updateHandlePosition() {
        Actor sole = soleSelected();
        if (sole == null) {
            return;
        }
        resizeHandle.setPosition(
                sole.getX() + sole.getWidth() - HANDLE_SIZE / 2f,
                sole.getY() - HANDLE_SIZE / 2f);
    }

    /**
     * Drags the target's bottom-right corner (top-left stays put) — the
     * handle itself just moves normally via {@code moveBy} (the same
     * proven idiom as {@link #makeMovable}); the target's new size/position
     * is derived from the handle's resulting absolute position relative to
     * a fixed anchor captured at {@code touchDown}, so nothing compounds
     * across frames even while the target's own size is being clamped.
     * Only active for a single selection — matches the old app, where the
     * handle was hidden for 0 or many selected widgets. Holding Shift locks
     * the aspect ratio the widget had when the drag started, like most
     * editors — not something the old JavaFX app had, added on request.
     */
    private void wireResizeHandle() {
        resizeHandle.addListener(new InputListener() {
            private float lastX, lastY;
            private float anchorX, anchorTop;
            private float anchorWidth, anchorHeight;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (soleSelected() == null) {
                    return false;
                }
                Actor sole = soleSelected();
                lastX = x;
                lastY = y;
                anchorX = sole.getX();
                anchorTop = sole.getY() + sole.getHeight();
                anchorWidth = sole.getWidth();
                anchorHeight = sole.getHeight();
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                Actor sole = soleSelected();
                if (sole == null) {
                    return;
                }
                resizeHandle.moveBy(x - lastX, y - lastY);

                // A Label/TextButton's own text doesn't rescale with setSize() — shrinking past
                // what it needs makes the text overflow the widget, so the handle can't drag it
                // smaller than that in the first place (see WidgetFactory.minWidth/minHeight).
                float minWidth = WidgetFactory.minWidth(sole, MIN_WIDGET_SIZE);
                float minHeight = WidgetFactory.minHeight(sole, MIN_WIDGET_SIZE);

                float cornerX = resizeHandle.getX() + HANDLE_SIZE / 2f;
                float cornerY = resizeHandle.getY() + HANDLE_SIZE / 2f;
                float newWidth = Math.max(minWidth, cornerX - anchorX);
                float newHeight = Math.max(minHeight, anchorTop - cornerY);

                boolean lockAspect = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                if (lockAspect && anchorWidth > 0 && anchorHeight > 0) {
                    // Scale by whichever axis moved further, so the drag still feels responsive
                    // regardless of which direction dominates — then re-clamp the scale itself
                    // (not each axis independently) so the content-minimum floor above can't
                    // break the ratio it's supposed to preserve.
                    float scale = Math.max(newWidth / anchorWidth, newHeight / anchorHeight);
                    scale = Math.max(scale, Math.max(minWidth / anchorWidth, minHeight / anchorHeight));
                    newWidth = anchorWidth * scale;
                    newHeight = anchorHeight * scale;
                    if (showGrid) {
                        // Snapping both axes independently would break the ratio — snap width to
                        // the grid and re-derive height from it instead.
                        newWidth = Math.max(minWidth, Math.round(newWidth / (float) GRID_SPACING) * GRID_SPACING);
                        newHeight = Math.max(minHeight, anchorHeight * (newWidth / anchorWidth));
                    }
                } else if (showGrid) {
                    newWidth = Math.max(minWidth, Math.round(newWidth / (float) GRID_SPACING) * GRID_SPACING);
                    newHeight = Math.max(minHeight, Math.round(newHeight / (float) GRID_SPACING) * GRID_SPACING);
                }

                sole.setSize(newWidth, newHeight);
                sole.setY(anchorTop - newHeight);

                if (sole.getUserObject() instanceof PlacedMeta meta) {
                    meta.width = (double) newWidth;
                    meta.height = (double) newHeight;
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                updateHandlePosition();
            }
        });
    }

    private void deleteSelected() {
        if (selectedActors.isEmpty()) {
            return;
        }
        for (Actor a : new ArrayList<>(expandToGroup(selectedActors))) {
            a.remove();
        }
        clearSelection();
    }

    // ---- in-app clipboard: copy/paste/duplicate (not the OS clipboard — see PlacedWidget snapshots below) ----

    private void copySelection() {
        clipboard = snapshotOf(expandToGroup(selectedActors));
    }

    private void duplicateSelection() {
        pasteWidgets(snapshotOf(expandToGroup(selectedActors)));
    }

    // ---- Ctrl+G / Ctrl+Shift+G: persistent grouping (see PlacedMeta#groupId's javadoc) ----

    private void groupSelection() {
        if (selectedActors.size() < 2) {
            return; // nothing meaningful to group
        }
        String groupId = UUID.randomUUID().toString();
        for (Actor a : selectedActors) {
            if (a.getUserObject() instanceof PlacedMeta meta) {
                meta.groupId = groupId;
            }
        }
    }

    private void ungroupSelection() {
        for (Actor a : expandToGroup(selectedActors)) {
            if (a.getUserObject() instanceof PlacedMeta meta) {
                meta.groupId = null;
            }
        }
    }

    private List<PlacedWidget> snapshotOf(Set<Actor> actors) {
        List<PlacedWidget> result = new ArrayList<>();
        for (Actor a : actors) {
            if (a.getUserObject() instanceof PlacedMeta meta) {
                double topLeftY = canvas.getHeight() - a.getY() - a.getHeight();
                result.add(new PlacedWidget(meta.id, meta.spec, a.getX(), topLeftY, meta.width, meta.height,
                        meta.anchorBaseId, meta.anchorAlignX, meta.anchorAlignY, meta.anchorOffsetX, meta.anchorOffsetY, meta.groupId));
            }
        }
        return result;
    }

    /** Recreates each snapshot offset by {@link #PASTE_OFFSET} with a fresh id (avoids colliding with the original) and skipping specs that no longer resolve against the current skin. New widgets become the selection. */
    private void pasteWidgets(List<PlacedWidget> source) {
        if (source.isEmpty() || skin == null) {
            return;
        }
        // Pasted copies of a group stay grouped with each other, but under a fresh id — sharing
        // the original groupId would incorrectly merge them into the same group as the source
        // widgets, which are still on the canvas as their own separate group.
        Map<String, String> groupIdRemap = new HashMap<>();
        List<Actor> pasted = new ArrayList<>();
        for (PlacedWidget pw : source) {
            Actor actor;
            try {
                actor = WidgetFactory.create(pw.spec(), skin);
            } catch (RuntimeException e) {
                continue;
            }
            applySize(actor, pw.width(), pw.height());
            double newX = pw.x() + PASTE_OFFSET;
            double newTopLeftY = pw.y() + PASTE_OFFSET;
            float stageY = canvas.getHeight() - (float) newTopLeftY - actor.getHeight();
            actor.setPosition((float) newX, stageY);

            String id = "widget-" + nextWidgetIndex.getAndIncrement();
            actor.setName(id);
            PlacedMeta meta = new PlacedMeta(id, pw.spec(), pw.width(), pw.height());
            // An anchored source keeps the same base/alignment — its position is derived, not
            // copied from x/y above, so the paste offset goes into the anchor offset instead
            // (AnchorResolver overwrites the position set above on the very next frame anyway).
            meta.anchorBaseId = pw.anchorBaseId();
            if (pw.anchorAlignX() != null) meta.anchorAlignX = pw.anchorAlignX();
            if (pw.anchorAlignY() != null) meta.anchorAlignY = pw.anchorAlignY();
            meta.anchorOffsetX = (pw.anchorOffsetX() != null ? pw.anchorOffsetX() : 0) + PASTE_OFFSET;
            meta.anchorOffsetY = (pw.anchorOffsetY() != null ? pw.anchorOffsetY() : 0) + PASTE_OFFSET;
            if (pw.groupId() != null) {
                meta.groupId = groupIdRemap.computeIfAbsent(pw.groupId(), old -> UUID.randomUUID().toString());
            }
            actor.setUserObject(meta);
            makeMovable(actor);
            canvas.addActor(actor);
            pasted.add(actor);
        }

        if (!pasted.isEmpty()) {
            // Already on top from the addActor order above — no separate toFront needed
            // (see replaceSelection's javadoc: selection alone must never reorder z-order).
            selectedActors.clear();
            selectedActors.addAll(pasted);
            refreshSelectionVisuals();
        }
    }

    /** Built fresh every frame for {@link AnchorResolver} — cheap enough (most widgets have no anchor, and there are never many placed at once) not to bother caching against add/remove/rename churn. */
    private Map<String, Actor> actorsByIdForAnchors() {
        Map<String, Actor> byId = new HashMap<>();
        for (Actor child : canvas.getChildren()) {
            if (child.getUserObject() instanceof PlacedMeta meta) {
                byId.put(meta.id, child);
            }
        }
        return byId;
    }

    /** Reads live actor positions back into immutable {@link PlacedWidget} records for export. */
    private List<PlacedWidget> snapshotPlacedWidgets() {
        List<PlacedWidget> result = new ArrayList<>();
        for (Actor child : canvas.getChildren()) {
            if (!(child.getUserObject() instanceof PlacedMeta meta)) {
                continue; // e.g. the background image or the resize handle, which carry no PlacedMeta
            }
            double topLeftY = canvas.getHeight() - child.getY() - child.getHeight();
            result.add(new PlacedWidget(meta.id, meta.spec, child.getX(), topLeftY, meta.width, meta.height,
                    meta.anchorBaseId, meta.anchorAlignX, meta.anchorAlignY, meta.anchorOffsetX, meta.anchorOffsetY, meta.groupId));
        }
        return result;
    }

    // ---- recent-file persistence ----

    private Path restoreLastLayout() {
        String lastLayoutFile = AppStorage.load().lastLayoutFile();
        if (lastLayoutFile == null) {
            return null;
        }
        Path path = Path.of(lastLayoutFile);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            openLayout(path);
            return path;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            menu.setStatus("Could not reopen the last layout: " + e.getMessage());
            return null;
        }
    }

    private void rememberLastLayout(Path layoutFile) {
        updateSettings(s -> new AppSettings(s.showingGrid(), layoutFile.toAbsolutePath().toString(),
                s.isLightTheme(), s.lastSkinDirectory()));
    }

    private void updateSettings(java.util.function.UnaryOperator<AppSettings> mutator) {
        AppStorage.save(mutator.apply(AppStorage.load()));
    }
}
