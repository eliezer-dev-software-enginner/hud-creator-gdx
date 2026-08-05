package my_app.skin;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>
 * Parsed with libGDX's own {@link JsonReader}, not a strict JSON library
 * (Jackson was tried first) - real skin.json files, e.g. what Skin Composer
 * exports, routinely use libGDX's lenient JSON dialect (unquoted keys and
 * string values), which a strict parser rejects outright. Using libGDX's own
 * reader guarantees whatever it accepts here is exactly what {@code Skin}
 * itself would accept, since it's the same parser.
 */
public final class SkinJsonParser {

    private SkinJsonParser() {
    }

    public record ParsedSkinJson(
            Map<String, SkinColor> colors,
            Map<String, String> fontFiles,
            Map<String, Map<String, Map<String, Object>>> styles
    ) {
    }

    public static ParsedSkinJson parse(Path skinJsonFile) throws IOException {
        String content = Files.readString(skinJsonFile);
        JsonValue root = new JsonReader().parse(content);

        Map<String, SkinColor> colors = new LinkedHashMap<>();
        Map<String, String> fontFiles = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Object>>> styles = new LinkedHashMap<>();

        for (JsonValue classEntry : root) {
            String simpleName = simpleClassName(classEntry.name);

            if (simpleName.equals("Color")) {
                parseColors(classEntry, colors);
            } else if (simpleName.equals("BitmapFont")) {
                parseFonts(classEntry, fontFiles);
            } else if (simpleName.endsWith("Style")) {
                styles.put(simpleName, parseStyles(classEntry));
            }
            // Other declared types (e.g. FreeTypeFontGenerator parameters) aren't
            // needed for loading/preview yet — skipped rather than guessed at.
        }

        return new ParsedSkinJson(colors, fontFiles, styles);
    }

    private static void parseColors(JsonValue instances, Map<String, SkinColor> out) {
        for (JsonValue c : instances) {
            out.put(c.name, new SkinColor(
                    c.getDouble("r", 0),
                    c.getDouble("g", 0),
                    c.getDouble("b", 0),
                    c.getDouble("a", 1)
            ));
        }
    }

    private static void parseFonts(JsonValue instances, Map<String, String> out) {
        for (JsonValue entry : instances) {
            String file = entry.getString("file", null);
            if (file != null) out.put(entry.name, file);
        }
    }

    private static Map<String, Map<String, Object>> parseStyles(JsonValue instances) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (JsonValue entry : instances) {
            out.put(entry.name, toFieldMap(entry));
        }
        return out;
    }

    private static Map<String, Object> toFieldMap(JsonValue styleNode) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (JsonValue field : styleNode) {
            fields.put(field.name, jsonValueToValue(field));
        }
        return fields;
    }

    private static Object jsonValueToValue(JsonValue value) {
        if (value.isString()) return value.asString();
        if (value.isBoolean()) return value.asBoolean();
        if (value.isLong()) return value.asLong();
        if (value.isDouble()) return value.asDouble();
        return value; // nested object/array (e.g. drawable insets) - left for the caller to interpret
    }

    private static String simpleClassName(String fullyQualifiedName) {
        int cut = Math.max(fullyQualifiedName.lastIndexOf('$'), fullyQualifiedName.lastIndexOf('.'));
        return cut >= 0 ? fullyQualifiedName.substring(cut + 1) : fullyQualifiedName;
    }
}
