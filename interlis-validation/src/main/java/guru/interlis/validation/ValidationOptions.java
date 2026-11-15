package guru.interlis.validation;

/**
 * Validation bounds and numeric rounding policy.
 *
 * @param maxDiagnosticExamples number of retained example diagnostics per session
 * @param maxNestedDepth maximum nested value container depth below an object
 * @param maxValuesPerObject maximum combined value visits, slot checks and expression steps per object
 * @param numericRounding rounds supplied numbers to model precision before validation
 */
public record ValidationOptions(int maxDiagnosticExamples, int maxNestedDepth, long maxValuesPerObject,
                                boolean numericRounding) {
    public ValidationOptions {
        if (maxDiagnosticExamples < 1 || maxNestedDepth < 1 || maxValuesPerObject < 1) {
            throw new IllegalArgumentException("Validation bounds must be positive");
        }
    }

    /** Uses the standard numeric rounding policy with the supplied bounds. */
    public ValidationOptions(int maxDiagnosticExamples, int maxNestedDepth, long maxValuesPerObject) {
        this(maxDiagnosticExamples, maxNestedDepth, maxValuesPerObject, true);
    }

    /** Copies these bounds with the requested numeric rounding policy. */
    public ValidationOptions withNumericRounding(boolean enabled) {
        return new ValidationOptions(maxDiagnosticExamples, maxNestedDepth, maxValuesPerObject, enabled);
    }

    /** Returns the standard limits for examples, depth and visited values. */
    public static ValidationOptions defaults() {
        return new ValidationOptions(200, 128, 1_000_000);
    }
}
