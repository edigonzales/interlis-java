package guru.interlis.model;

/** API misuse: a handle from a different snapshot was used. */
public final class SnapshotMismatchException extends IllegalArgumentException {
    private final SnapshotId expected;
    private final SnapshotId actual;

    /**
     * Creates an instance.
     *
     * @param expected the {@code expected} argument
     * @param actual the {@code actual} argument
     */
    public SnapshotMismatchException(SnapshotId expected, SnapshotId actual) {
        super("snapshot mismatch: expected " + expected.value() + " but got " + actual.value());
        this.expected = expected;
        this.actual = actual;
    }

    /**
     * Returns the {@code expected} value.
     *
     * @return the value
     */
    public SnapshotId expected() {
        return expected;
    }

    /**
     * Returns the {@code actual} value.
     *
     * @return the value
     */
    public SnapshotId actual() {
        return actual;
    }
}
