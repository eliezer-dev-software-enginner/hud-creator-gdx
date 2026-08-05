package my_app.skin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads a libGDX {@code Skin} JSON file. Top-level keys are fully-qualified
 * class names (e.g. {@code "com.badlogic.gdx.scenes.scene2d.ui.Button$ButtonStyle"})
 * mapping to named instances of that class. {@code Color} and {@code BitmapFont}
 * get dedicated handling since other styles reference them by name; every
 * class whose simple name ends in {@code Style} (ButtonStyle, LabelStyle,
 * WindowStyle, ...) is kept generically as a name → field-map, since resolving
 * each field (drawable region name vs. color name vs. plain number) depends on
 * the widget type and is a rendering-phase concern, not a parsing one.
 */
public final class SkinJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SkinJsonParser() {
    }

    public record ParsedSkinJson(
            Map<String, SkinColor> colors,
            Map<String, String> fontFiles,
            Map<String, Map<String, Map<String, Object>>> styles
    ) {
    }

    public static ParsedSkinJson parse(Path skinJsonFile) throws IOException {
        JsonNode root = MAPPER.readTree(skinJsonFile.toFile());

        Map<String, SkinColor> colors = new LinkedHashMap<>();
        Map<String, String> fontFiles = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> styles = new LinkedHashMap<>();

        Iterator<Map.Entry<String, JsonNode>> classEntries = root.fields();
        while (classEntries.hasNext()) {
            Map.Entry<String, JsonNode> classEntry = classEntries.next();
            String simpleName = simpleClassName(classEntry.getKey());
            JsonNode instances = classEntry.getValue();

            if (simpleName.equals("Color")) {
                parseColors(instances, colors);
            } else if (simpleName.equals("BitmapFont")) {
                parseFonts(instances, fontFiles);
            } else if (simpleName.endsWith("Style")) {
                styles.put(simpleName, parseStyles(instances));
            }
            // Other declared types (e.g. FreeTypeFontGenerator parameters) aren't
            // needed for loading/preview yet — skipped rather than guessed at.
        }

        return new ParsedSkinJson(colors, fontFiles, styles);
    }

    private static void parseColors(JsonNode instances, Map<String, SkinColor> out) {
        instances.fields().forEachRemaining(entry -> {
            JsonNode c = entry.getValue();
            out.put(entry.getKey(), new SkinColor(
                    c.path("r").asDouble(0),
                    c.path("g").asDouble(0),
                    c.path("b").asDouble(0),
                    c.path("a").asDouble(1)
            ));
        });
    }

    private static void parseFonts(JsonNode instances, Map<String, String> out) {
        instances.fields().forEachRemaining(entry -> {
            String file = entry.getValue().path("file").asText(null);
            if (file != null) out.put(entry.getKey(), file);
        });
    }

    private static Map<String, Map<String, Object>> parseStyles(JsonNode instances) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        instances.fields().forEachRemaining(entry -> out.put(entry.getKey(), toFieldMap(entry.getValue())));
        return out;
    }

    private static Map<String, Object> toFieldMap(JsonNode styleNode) {
        Map<String, Object> fields = new LinkedHashMap<>();
        styleNode.fields().forEachRemaining(field -> fields.put(field.getKey(), jsonNodeToValue(field.getValue())));
        return fields;
    }

    private static Object jsonNodeToValue(JsonNode value) {
        if (value.isTextual()) return value.asText();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isIntegralNumber()) return value.asLong();
        if (value.isFloatingPointNumber()) return value.asDouble();
        return value; // nested object/array (e.g. drawable insets) - left for the caller to interpret
    }

    private static String simpleClassName(String fullyQualifiedName) {
        int cut = Math.max(fullyQualifiedName.lastIndexOf('$'), fullyQualifiedName.lastIndexOf('.'));
        return cut >= 0 ? fullyQualifiedName.substring(cut + 1) : fullyQualifiedName;
    }
}
