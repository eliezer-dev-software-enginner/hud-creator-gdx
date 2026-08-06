package my_app;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import megalodonte.base.components.Component;
import megalodonte.base.state.ReadableState;
import megalodonte.base.theme.ThemeInterface;
import megalodonte.base.state.State;
import megalodonte.components.TextFlow;
import megalodonte.components.v2.Scroll;
import megalodonte.components.SpacerHorizontal;
import megalodonte.components.Text;
import megalodonte.components.inputs.OnChangeResult;
import megalodonte.components.layout_components.Column;
import megalodonte.components.layout_components.Row;
import megalodonte.components.v2.Input;
import megalodonte.props.ColumnProps;
import megalodonte.props.TextProps;
import megalodonte.props.v2.InputProps;
import megalodonte.v2.Show;
import my_app.skin.AtlasRegion;
import my_app.skin.SkinModel;
import my_app.skin.render.AtlasImageView;
import my_app.skin.render.DrawableView;
import my_app.skin.render.SkinImages;
import my_app.widget.DragPayload;
import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;
import my_app.widget.render.WidgetViews;

import java.util.Comparator;
import java.util.List;

/**
 * The "Palette" sidebar: reacts to the app's loaded {@link SkinModel} and
 * shows, once one's loaded — a "Properties" panel for the widget currently
 * selected on the Canva (if any), a draggable widget catalog (one entry per
 * Button/TextButton/Label style found), and the raw atlas region list (also
 * draggable, dropped as a plain {@link WidgetSpec.ImageSpec}) — or an
 * empty/error state otherwise.
 */
final class SkinPalettePanel {

    private static final double WIDTH = 260;
    private static final double PREVIEW_MAX_EDGE = 40;

    private final Column root;
    /** Populated by {@link #rebuildCatalog} whenever a skin is loaded; only visible while {@link Show} says so. */
    private final Column catalogArea = new Column(new ColumnProps().spacingOf(12).fillHeight());
    private final CanvasController canvasController;

    SkinPalettePanel(HomeScreenViewModel viewModel, CanvasController canvasController, ThemeInterface theme) {
        this.canvasController = canvasController;
        this.root = new Column(new ColumnProps()
                .spacingOf(12)
                .paddingAll(12)
                .minWidth(WIDTH)
                .width(WIDTH)
                .maxWidth(WIDTH)
                .fillHeight()
                .bgColor(theme.colors().surface()));

        ReadableState<Boolean> hasError = viewModel.loadErrorState().map(error -> error != null);
        ReadableState<Boolean> hasSkin = viewModel.skinState().map(skin -> skin != null);

        Component errorText = new TextFlow(new Text(
                viewModel.loadErrorState().map(error -> error == null ? "" : error),
                new TextProps().color(Themes.ERROR_TEXT_COLOR)));
        Component noSkinText = new TextFlow(new Text("No skin loaded. Use Options → Load Skin."));

        Component body = Show.when(hasError,
                () -> errorText,
                () -> Show.when(hasSkin, () -> catalogArea, () -> noSkinText).fillHeight())
                .fillHeight();

        root.children(new Text("Palette", new TextProps().bold()), body);

        // The error/empty-state switch above is fully reactive (Show + a Text bound
        // straight to loadErrorState()) - only the catalog itself, whose row count
        // depends on the loaded skin's styles/regions, still needs an imperative
        // rebuild when the skin or the selected widget changes.
        viewModel.skinState().subscribe(skin -> rebuildCatalog(skin, viewModel));
        // Deliberately NOT subscribed to placedWidgets() — every keystroke in the
        // nickname field below updates it, and rebuilding on every keystroke would
        // tear down the very Input the user is typing into.
        viewModel.selectedWidgetIdState().subscribe(id -> rebuildCatalog(viewModel.skinState().get(), viewModel));
    }

    Component asComponent() {
        return root;
    }

    /** {@link Column} is declarative/append-only (no "clear and rebuild" API) — the catalog's row count is only known once a skin is loaded, so repopulating it still needs direct access to the underlying VBox. */
    private void rebuildCatalog(SkinModel skin, HomeScreenViewModel viewModel) {
        if (skin == null) return; // Show hides catalogArea in this case; nothing to build

        VBox catalogNode = (VBox) catalogArea.getNode();
        catalogNode.getChildren().clear();

        Image atlasImage = SkinImages.loadAtlasImage(skin);

        catalogNode.getChildren().add(new Text(skin.skinJsonPath().getFileName().toString()).getNode());

        Component properties = buildPropertiesSection(viewModel);
        if (properties != null) {
            catalogNode.getChildren().add(properties.getNode());
        }

        addCatalogSection(catalogNode, skin, atlasImage, "Buttons", "ButtonStyle",
                styleName -> new WidgetSpec.ButtonSpec(styleName));
        addCatalogSection(catalogNode, skin, atlasImage, "Buttons with text", "TextButtonStyle",
                styleName -> new WidgetSpec.TextButtonSpec(styleName, "Button"));
        addCatalogSection(catalogNode, skin, atlasImage, "Texts", "LabelStyle",
                styleName -> new WidgetSpec.LabelSpec(styleName, "Text"));

        catalogNode.getChildren().add(sectionHeader("Regions").getNode());
        catalogNode.getChildren().add(buildRegionList(skin, atlasImage).getNode());
    }

    /** Shows the selected widget's id/type and a live-editable nickname field, or null if no widget is selected. */
    private Component buildPropertiesSection(HomeScreenViewModel viewModel) {
        String selectedId = viewModel.selectedWidgetIdState().get();
        if (selectedId == null) return null;

        PlacedWidget widget = viewModel.placedWidgets().get().stream()
                .filter(w -> w.id().equals(selectedId))
                .findFirst()
                .orElse(null);
        if (widget == null) return null;

        State<String> nicknameText = State.of(widget.nickname() == null ? "" : widget.nickname());
        Component nicknameInput = new Input(nicknameText, new InputProps().width(150))
                .onChange(value -> {
                    canvasController.setNickname(widget.id(), value.isBlank() ? null : value.trim());
                    return OnChangeResult.same(value);
                });

        Row row = new Row().children(new Text("Nickname:"), new SpacerHorizontal(6), nicknameInput);

        return new Column(new ColumnProps().spacingOf(6)).children(
                sectionHeader("Properties"),
                new Text(widget.id() + " — " + widgetTypeLabel(widget.spec())),
                row
        );
    }

    private static String widgetTypeLabel(WidgetSpec spec) {
        return switch (spec) {
            case WidgetSpec.ImageSpec s -> "Image (" + s.regionName() + ")";
            case WidgetSpec.ButtonSpec s -> "Button (" + s.styleName() + ")";
            case WidgetSpec.TextButtonSpec s -> "Button with text (" + s.styleName() + ")";
            case WidgetSpec.LabelSpec s -> "Text (" + s.styleName() + ")";
        };
    }

    private void addCatalogSection(VBox catalogNode, SkinModel skin, Image atlasImage, String heading, String styleClass,
                                    java.util.function.Function<String, WidgetSpec> specFactory) {
        List<String> styleNames = skin.styleNames(styleClass).stream().sorted().toList();
        if (styleNames.isEmpty()) return;

        catalogNode.getChildren().add(sectionHeader(heading).getNode());

        Column list = new Column(new ColumnProps().spacingOf(6));
        for (String styleName : styleNames) {
            WidgetSpec spec = specFactory.apply(styleName);
            Component preview = WidgetViews.build(skin, atlasImage, spec);
            makeDraggable(preview.getNode(), spec);

            Row row = new Row().children(preview, new SpacerHorizontal(8), new Text(styleName));
            list.children(row);
        }
        catalogNode.getChildren().add(list.getNode());
    }

    private static Component sectionHeader(String text) {
        return new Text(text, new TextProps().bold().fontSize(13));
    }

    private Component buildRegionList(SkinModel skin, Image atlasImage) {
        List<AtlasRegion> regions = skin.regions().stream()
                .sorted(Comparator.comparing(AtlasRegion::name))
                .toList();

        Column list = new Column(new ColumnProps().spacingOf(6));
        for (AtlasRegion region : regions) {
            Component preview = DrawableView.of(atlasImage, region);
            // Most regions are small icons/buttons, but atlases also carry the
            // full BitmapFont glyph page as a plain region — cap it down so one
            // oversized thumbnail can't blow out the list's width.
            if (preview instanceof AtlasImageView atlasImageView) {
                atlasImageView.imageView().setFitWidth(PREVIEW_MAX_EDGE);
                atlasImageView.imageView().setFitHeight(PREVIEW_MAX_EDGE);
                atlasImageView.imageView().setPreserveRatio(true);
            }
            makeDraggable(preview.getNode(), new WidgetSpec.ImageSpec(region.name()));

            Row row = new Row().children(preview, new SpacerHorizontal(8), new Text(region.name()));
            list.children(row);
        }

        return new Scroll(list);
    }

    /** Lets {@code previewNode} be dragged onto the Canva, carrying {@code spec} via {@link DragPayload}. */
    private void makeDraggable(Node previewNode, WidgetSpec spec) {
        previewNode.setCursor(Cursor.OPEN_HAND);
        previewNode.setOnDragDetected(event -> {
            DragPayload.set(spec);

            var dragboard = previewNode.startDragAndDrop(TransferMode.COPY);
            var content = new ClipboardContent();
            content.putString(spec.toString()); // Dragboard needs a real content entry; the actual payload rides DragPayload.
            dragboard.setContent(content);
            dragboard.setDragView(previewNode.snapshot(new SnapshotParameters(), null));

            event.consume();
        });
    }
}
