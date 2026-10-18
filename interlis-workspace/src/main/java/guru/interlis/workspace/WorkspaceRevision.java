package guru.interlis.workspace;

/**
 * Monotonically increasing revision of one workspace.
 *
 * @param value wrapped value
 */
public record WorkspaceRevision(long value) {
    /** Creates a validated instance. */
    public WorkspaceRevision {
        if (value < 0) {
            throw new IllegalArgumentException("WorkspaceRevision must be non-negative");
        }
    }

    /**
     * Returns the {@code initial} value.
     *
     * @return the value
     */
    public static WorkspaceRevision initial() {
        return new WorkspaceRevision(0);
    }
}
