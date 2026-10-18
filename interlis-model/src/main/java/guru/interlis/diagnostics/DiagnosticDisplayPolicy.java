package guru.interlis.diagnostics;

/**
 * Presentation policy. It never changes the canonical diagnostic list and
 * cannot turn an incomplete analysis into a successful one.
 */
public record DiagnosticDisplayPolicy(boolean suppressDependentOnVisibleError,
                                      int maxVisible) {
    /** Creates a validated instance. */
    public DiagnosticDisplayPolicy {
        if (maxVisible < 0) {
            throw new IllegalArgumentException("maxVisible must be non-negative");
        }
    }

    /**
     * Executes {@code all}.
     *
     * @return the result
     */
    public static DiagnosticDisplayPolicy all() {
        return new DiagnosticDisplayPolicy(false, Integer.MAX_VALUE);
    }
}
