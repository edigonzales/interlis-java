package guru.interlis.workspace.resolution;

/** The expected inventory fingerprint does not match the provided snapshot. */
public final class StaleInputException extends IllegalArgumentException {
    /**
     * Creates an instance.
     *
     * @param expected the {@code expected} argument
     * @param actual the {@code actual} argument
     */
    public StaleInputException(String expected, String actual) {
        super("stale inventory: expected " + expected + " but got " + actual);
    }
}
