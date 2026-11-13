package guru.interlis.validation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable report for a completed transfer. Counts include omitted diagnostic examples. */
public record ValidationReport(Result result, long inspectedBaskets, long inspectedObjects, long detectedViolations,
                               List<ValidationDiagnostic> examples, ValidationCoverage coverage,
                               Optional<DatasetScope> datasetScope) {
    public ValidationReport {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(datasetScope, "datasetScope");
        examples = List.copyOf(examples);
        if (inspectedBaskets < 0 || inspectedObjects < 0 || detectedViolations < examples.size()
                || (detectedViolations == 0) != (result == Result.PASSED_CHECKED_SCOPE)) {
            throw new IllegalArgumentException("Inconsistent validation counts or result");
        }
    }

    /** Outcome limited to the rules the session checked. */
    public enum Result {
        PASSED_CHECKED_SCOPE, FAILED_CHECKED_SCOPE
    }
}
