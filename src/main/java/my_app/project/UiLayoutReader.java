package my_app.project;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/** Reads back a {@link UiLayout} written by {@link UiLayoutWriter} — used by "Load Layout". */
public final class UiLayoutReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UiLayoutReader() {
    }

    public static UiLayout read(Path layoutFile) throws IOException {
        return MAPPER.readValue(layoutFile.toFile(), UiLayout.class);
    }
}
