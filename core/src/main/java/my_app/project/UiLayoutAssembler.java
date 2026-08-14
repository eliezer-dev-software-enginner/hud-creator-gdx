package my_app.project;

import my_app.widget.PlacedWidget;
import my_app.widget.WidgetSpec;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Converts between the app's in-memory widget list and the {@link UiLayout} JSON shape. */
public final class UiLayoutAssembler {

    private static final int FORMAT_VERSION = 1;

    private UiLayoutAssembler() {
    }

    public static UiLayout assemble(String skinPath, int canvasWidth, int canvasHeight,
                                     String backgroundImagePath, List<PlacedWidget> placedWidgets) {
        List<PlacedWidgetDto> widgets = placedWidgets.stream()
                .map(UiLayoutAssembler::toDto)
                .toList();
        return new UiLayout(FORMAT_VERSION, skinPath, canvasWidth, canvasHeight, backgroundImagePath, widgets);
    }

    /**
     * Path from {@code outputFile}'s directory to {@code target}, using
     * {@code /} regardless of OS so the JSON stays portable. Falls back to an
     * absolute path if the two aren't relativizable (e.g. separate drives on
     * Windows) — still functional, just not portable across machines.
     */
    public static String relativePath(Path target, Path outputFile) {
        Path targetAbs = target.toAbsolutePath().normalize();
        try {
            Path outputDir = outputFile.toAbsolutePath().normalize().getParent();
            return outputDir.relativize(targetAbs).toString().replace(File.separatorChar, '/');
        } catch (IllegalArgumentException e) {
            return targetAbs.toString().replace(File.separatorChar, '/');
        }
    }

    private static PlacedWidgetDto toDto(PlacedWidget widget) {
        return switch (widget.spec()) {
            case WidgetSpec.ImageSpec s -> new PlacedWidgetDto(
                    widget.id(), "image", null, s.regionName(), null, widget.x(), widget.y(), widget.width(), widget.height(), null,
                    widget.anchorBaseId(), widget.anchorAlignX(), widget.anchorAlignY(), widget.anchorOffsetX(), widget.anchorOffsetY(), widget.groupId());
            case WidgetSpec.ButtonSpec s -> new PlacedWidgetDto(
                    widget.id(), "button", s.styleName(), null, null, widget.x(), widget.y(), widget.width(), widget.height(), null,
                    widget.anchorBaseId(), widget.anchorAlignX(), widget.anchorAlignY(), widget.anchorOffsetX(), widget.anchorOffsetY(), widget.groupId());
            case WidgetSpec.TextButtonSpec s -> new PlacedWidgetDto(
                    widget.id(), "textButton", s.styleName(), null, s.text(), widget.x(), widget.y(), widget.width(), widget.height(), s.fontColor(),
                    widget.anchorBaseId(), widget.anchorAlignX(), widget.anchorAlignY(), widget.anchorOffsetX(), widget.anchorOffsetY(), widget.groupId());
            case WidgetSpec.LabelSpec s -> new PlacedWidgetDto(
                    widget.id(), "label", s.styleName(), null, s.text(), widget.x(), widget.y(), widget.width(), widget.height(), s.fontColor(),
                    widget.anchorBaseId(), widget.anchorAlignX(), widget.anchorAlignY(), widget.anchorOffsetX(), widget.anchorOffsetY(), widget.groupId());
        };
    }

    /** The reverse of {@link #toDto} — used when reloading a layout back into the builder. */
    public static PlacedWidget fromDto(PlacedWidgetDto dto) {
        WidgetSpec spec = switch (dto.type()) {
            case "image" -> new WidgetSpec.ImageSpec(dto.regionName());
            case "button" -> new WidgetSpec.ButtonSpec(dto.styleName());
            case "textButton" -> new WidgetSpec.TextButtonSpec(dto.styleName(), dto.text(), dto.fontColor());
            case "label" -> new WidgetSpec.LabelSpec(dto.styleName(), dto.text(), dto.fontColor());
            default -> throw new IllegalArgumentException("Unknown widget type: \"" + dto.type() + "\"");
        };
        return new PlacedWidget(dto.id(), spec, dto.x(), dto.y(), dto.width(), dto.height(),
                dto.anchorBaseId(), dto.anchorAlignX(), dto.anchorAlignY(), dto.anchorOffsetX(), dto.anchorOffsetY(), dto.groupId());
    }
}
