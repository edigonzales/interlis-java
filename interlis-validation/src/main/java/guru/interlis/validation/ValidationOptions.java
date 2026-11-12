package guru.interlis.validation;

/**
 * Positive bounds per session or per object.
 *
 * @param maxDiagnosticExamples number of retained example diagnostics per session
 * @param maxNestedDepth maximum nested value container depth below an object
 * @param maxValuesPerObject maximum combined supplied-value visits and model slot/element checks per object
 */
public record ValidationOptions(int maxDiagnosticExamples, int maxNestedDepth, long maxValuesPerObject) {
    public ValidationOptions {
        if (maxDiagnosticExamples < 1 || maxNestedDepth < 1 || maxValuesPerObject < 1) {
            throw new IllegalArgumentException("Validation bounds must be positive");
        }
    }

    /** Returns the standard limits for examples, depth and visited values. */
    public static ValidationOptions defaults() {
        return new ValidationOptions(200, 128, 1_000_000);
    }
}
