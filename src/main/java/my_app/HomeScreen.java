package my_app;

import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import megalodonte.base.components.Component;
import megalodonte.base.components.ScreenComponent;
import megalodonte.base.theme.ThemeInterface;
import megalodonte.base.theme.ThemeManager;
import megalodonte.components.*;
import megalodonte.components.layout_components.Canva;
import megalodonte.components.layout_components.Column;
import megalodonte.components.layout_components.Container;
import megalodonte.components.layout_components.Row;
import megalodonte.props.CanvaProps;
import megalodonte.props.ColumnProps;
import megalodonte.props.ContainerProps;
import megalodonte.props.RowProps;
import my_app.widget.DragPayload;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class HomeScreen implements ScreenComponent {

    private final HomeScreenViewModel viewModel;
    private final ThemeInterface currentTheme;

    public HomeScreen(HomeScreenViewModel viewModel, ThemeInterface currentTheme) {
        this.viewModel = viewModel;
        this.currentTheme = currentTheme;
    }

    /** Restores the grid toggle and auto-reopens the last layout JSON (see {@link my_app.storage.AppStorage}) — no dialog, unlike "Load Layout" in the menu. */
    @Override
    public void onMount() {
        viewModel.restoreFromAppStorage();
    }

    @Override
    public Component render() {
        Canva canva = new Canva(new CanvaProps()
                .borderColor(Themes.CANVAS_BORDER_COLOR)
                .borderWidth(1)
                // Clicking empty canvas (not a widget - those consume their own click,
                // see CanvasController) deselects, closing the "Properties" panel.
                .onClick(() -> viewModel.selectedWidgetIdState().set(null)));
        bindCanvasSize(canva, viewModel);
        bindCanvasBackgroundImage(canva, viewModel, currentTheme);

        CanvasController canvasController = new CanvasController(canva, viewModel);
        viewModel.attachCanvasController(canvasController);
        wireCanvasDropTarget(canva, canvasController);

        SkinPalettePanel palette = new SkinPalettePanel(viewModel, canvasController, currentTheme);

        Column canvasArea = new Column(new ColumnProps().spacingOf(8).fillWidth()).children(
                CanvasSizeToolbar.build(viewModel),
                canva
        );

        Row workspace = new Row(new RowProps().fillHeight()).children(
                canvasArea,
                palette.asComponent()
        );

        return new Container(new ContainerProps().fillHeight().bgColor(currentTheme.colors().background())).children(
                new MenuBar().menu(new Menu("Options")
                                .item("New",()->viewModel.handleNew())
                                .item("Load Skin",()->viewModel.handleLoad())
                                .item("Load Layout",()->viewModel.handleLoadLayout())
                                .item("Save",()->viewModel.handleSave())
                                .item("Export",()->viewModel.handleExport())
                                .item("Image of Background (Canva)",()->viewModel.handleLoadBackgroundImage())
                                .item("Remove Image of Background",()->viewModel.handleClearBackgroundImage())
                        )
                        .trailing(themeToggleIcon(currentTheme)),
                new SpacerVertical(10),
                workspace,
                buildStatusBar(viewModel, currentTheme)
        );
    }

    /**
     * Sun/moon icon docked at the end of the menu bar — shows the theme
     * currently active (sun for {@link Themes#light}, moon for
     * {@link Themes#dark}), click toggles to the other one via
     * {@link ThemeManager#setTheme}. No local state to manage here: Main
     * already rebuilds the whole screen reactively whenever the active theme
     * changes.
     */
    private static Component themeToggleIcon(ThemeInterface theme) {
        boolean isLight = theme == Themes.light;
        Ikon icon = isLight ? FontAwesomeSolid.SUN : FontAwesomeSolid.MOON;

        return new Clickable(
                Component.CreateFromJavaFxNode(
                        FontIcon.of(icon, 18, Color.web(theme.colors().textPrimary()))))
                .onClick(() -> ThemeManager.setTheme(isLight ? Themes.dark : Themes.light));
    }

    /** Feedback from the last Save/Export attempt. */
    private static Component buildStatusBar(HomeScreenViewModel viewModel, ThemeInterface theme) {
        return new Row(new RowProps().paddingTop(6).paddingRight(12).paddingDown(6).paddingLeft(12)
                        .bgColor(theme.colors().hover())
        ).children(new Text(viewModel.statusMessageState()));
    }

    /** The Canva's plain background — {@code theme.colors().surface()}, same slot cards/panels use. */
    private static Background surfaceBackground(ThemeInterface theme) {
        return new Background(new BackgroundFill(Color.web(theme.colors().surface()), null, null));
    }

    /**
     * Shows/hides a reference background image behind the Canva's contents —
     * purely visual (see {@link HomeScreenViewModel#backgroundImagePathState()}'s
     * Javadoc for why it's saved in the JSON but still not real UI content).
     * Uses the Java {@code setBackground(...)} API exclusively (not an inline
     * {@code -fx-background-color}, unlike the border, which goes through
     * surface color and the image doesn't fight CSS.
     */
    private static void bindCanvasBackgroundImage(Canva canva, HomeScreenViewModel viewModel, ThemeInterface theme) {
        Region pane = (Region) canva.getNode();
        viewModel.backgroundImagePathState().subscribe(path -> {
            if (path == null) {
                pane.setBackground(surfaceBackground(theme));
                return;
            }
            javafx.scene.image.Image image = new javafx.scene.image.Image(java.nio.file.Path.of(path).toUri().toString());
            BackgroundImage backgroundImage = new BackgroundImage(
                    image,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    new BackgroundSize(1.0, 1.0, true, true, false, true) // cover
            );
            pane.setBackground(new Background(backgroundImage));
        });
    }

    /** Keeps the Canva's actual pixel size (and clip, so dropped widgets can't spill past the "screen" edges) in sync with {@link CanvasSizeToolbar}'s inputs. */
    private static void bindCanvasSize(Canva canva, HomeScreenViewModel viewModel) {
        Region pane = (Region) canva.getNode();
        Rectangle clip = new Rectangle();
        pane.setClip(clip);

        Runnable applySize = () -> {
            double width = viewModel.canvasWidthState().get();
            double height = viewModel.canvasHeightState().get();
            pane.setMinSize(width, height);
            pane.setPrefSize(width, height);
            pane.setMaxSize(width, height);
            clip.setWidth(width);
            clip.setHeight(height);
        };
        viewModel.canvasWidthState().subscribe(w -> applySize.run());
        viewModel.canvasHeightState().subscribe(h -> applySize.run());
    }

    /** Accepts drops carrying a {@link DragPayload} and hands them off to {@link CanvasController}. */
    private static void wireCanvasDropTarget(Canva canva, CanvasController controller) {
        Region pane = (Region) canva.getNode();

        pane.setOnDragOver(event -> {
            if (DragPayload.get() != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        pane.setOnDragDropped(event -> {
            var spec = DragPayload.get();
            boolean accepted = spec != null;
            if (accepted) {
                controller.place(spec, event.getX(), event.getY());
            }
            event.setDropCompleted(accepted);
            event.consume();
        });

        pane.setOnDragDone(event -> DragPayload.clear());
    }

}
