package guru.interlis.cli;

/**
 * Standard compiler process policy: model/operational rejection is exit 1,
 * usage is 2, internal failure is 3 and controlled cancellation is 4.
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
            case SOURCE_REJECTED, FORMAT_CHECK_FAILED, INCOMPLETE_ANALYSIS,
                    INPUT_UNRESOLVED, WARNINGS_REJECTED, OPERATIONAL_FAILURE -> 1;
            case USAGE_ERROR -> 2;
            case INTERNAL_FAILURE -> 3;
            case CANCELLED -> 4;
        };
    }
}
