package guru.interlis.cli;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.RejectionReason;

/** Maps the gate summary to CLI status without re-evaluating compiler rules. */
public final class CliOutcomeMapper {
    /**
     * Executes {@code classify}.
     *
     * @param outcome the {@code outcome} argument
     *
     * @return the result
     */
    public CliTermination classify(CompilationOutcome outcome) {
        return switch (outcome) {
            case CompilationOutcome.Succeeded ignored -> CliTermination.SUCCESS;
            case CompilationOutcome.Rejected rejected -> classify(rejected);
            case CompilationOutcome.Cancelled ignored -> CliTermination.CANCELLED;
            case CompilationOutcome.Failed ignored -> CliTermination.INTERNAL_FAILURE;
        };
    }

    private CliTermination classify(CompilationOutcome.Rejected rejected) {
        var reasons = rejected.rejection().reasons();
        if (reasons.contains(RejectionReason.ENVIRONMENT_FAILURE)) {
            return CliTermination.OPERATIONAL_FAILURE;
        }
        if (reasons.contains(RejectionReason.INCOMPLETE_SUPPORT)) {
            return CliTermination.INCOMPLETE_ANALYSIS;
        }
        if (reasons.contains(RejectionReason.MISSING_INPUT)
                || reasons.contains(RejectionReason.AMBIGUOUS_INPUT)) {
            return CliTermination.INPUT_UNRESOLVED;
        }
        if (reasons.contains(RejectionReason.SOURCE_ERRORS)) {
            return CliTermination.SOURCE_REJECTED;
        }
        return CliTermination.WARNINGS_REJECTED;
    }
}
