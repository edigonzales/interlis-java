package guru.interlis.bridge;

/** Typed protocol failure, separate from INTERLIS compiler diagnostics. */
public final class BridgeException extends RuntimeException {
    private final String code;
    private final JsonValue.JsonObject details;

    /**
     * Creates an instance.
     *
     * @param code the {@code code} argument
     * @param message the {@code message} argument
     */
    public BridgeException(String code, String message) {
        this(code, message, new JsonValue.JsonObject(java.util.Map.of()));
    }

    /**
     * Creates an instance.
     *
     * @param code the {@code code} argument
     * @param message the {@code message} argument
     * @param details the {@code details} argument
     */
    public BridgeException(String code, String message, JsonValue.JsonObject details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    /**
     * Returns the {@code code} value.
     *
     * @return the value
     */
    public String code() {
        return code;
    }

    /**
     * Returns the {@code details} value.
     *
     * @return the value
     */
    public JsonValue.JsonObject details() {
        return details;
    }
}
