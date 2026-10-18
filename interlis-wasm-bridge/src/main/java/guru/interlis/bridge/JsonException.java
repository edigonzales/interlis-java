package guru.interlis.bridge;

/** Invalid protocol JSON. */
public final class JsonException extends RuntimeException {
    /**
     * Creates an instance.
     *
     * @param message the {@code message} argument
     */
    public JsonException(String message) {
        super(message);
    }
}
