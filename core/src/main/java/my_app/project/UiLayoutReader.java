package my_app.project;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;

/** Reads back a {@link UiLayout} written by {@link UiLayoutWriter} — used by "Abrir Layout". */
public final class UiLayoutReader {

    /**
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} is on by default — without
     * disabling it, a layout saved by an older build with a field this
     * one no longer has (e.g. the removed {@code nickname}) would throw
     * instead of just loading with that field ignored.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private UiLayoutReader() {
    }

    public static UiLayout read(Path layoutFile) throws IOException {
        return MAPPER.readValue(layoutFile.toFile(), UiLayout.class);
    }
}
