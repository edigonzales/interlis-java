package guru.interlis.model;

/** Stable reason why a query could not be analyzed. */
public enum NotAnalyzedReason {
    /** The {@code NOT_REQUESTED} constant. */
    NOT_REQUESTED,
    /** The {@code PENDING} constant. */
    PENDING,
    /** The {@code CANCELLED} constant. */
    CANCELLED,
    /** The {@code BUDGET_EXCEEDED} constant. */
    BUDGET_EXCEEDED,
    /** The {@code MISSING_IMPLEMENTATION} constant. */
    MISSING_IMPLEMENTATION,
    /** The {@code OUTSIDE_SELECTION} constant. */
    OUTSIDE_SELECTION
}
