package guru.interlis.cli;

/**
 * Default process policy. All model-level rejection statuses share exit code 1
 * until a pinned legacy profile supplies a different mapping.
 */
public final class ExitCodePolicy {
    /**
     * Executes {@code exitCode}.
     *
     * @param termination the {@code termination} argument
     *
     * @return the result
     */
    public int exitCode(CliTermination termination) {
        return switch (termination) {
            case SUCCESS -> 0;
            case SOURCE_REJECTED, INCOMPLETE_ANALYSIS, INPUT_UNRESOLVED,
                    WARNINGS_REJECTED, OPERATIONAL_FAILURE -> 1;
            case USAGE_ERROR -> 2;
            case INTERNAL_FAILURE -> 3;
            case CANCELLED -> 4;
        };
    }
}
