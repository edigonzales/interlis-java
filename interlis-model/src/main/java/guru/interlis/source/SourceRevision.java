package guru.interlis.source;

/**
 * Monotonically increasing revision of one source inside one workspace.
 *
 * @param value non-negative revision number
 */
public record SourceRevision(long value) {
    /** Creates a validated instance. */
    public SourceRevision {
        if (value < 0) {
            throw new IllegalArgumentException("SourceRevision must be non-negative");
        }
    }

    /**
     * Executes {@code initial}.
     *
     * @return the result
     */
    public static SourceRevision initial() {
        return new SourceRevision(0);
    }
}
