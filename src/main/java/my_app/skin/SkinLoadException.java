package my_app.skin;

/** Thrown when a skin (JSON, atlas, or referenced image) can't be parsed or is missing. */
public class SkinLoadException extends RuntimeException {

    public SkinLoadException(String message) {
        super(message);
    }

    public SkinLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
