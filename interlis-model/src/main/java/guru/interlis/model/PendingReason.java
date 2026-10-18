package guru.interlis.model;

/** Why a type has not been analyzed yet. */
public enum PendingReason {
    /** The {@code NOT_ANALYZED} constant. */
    NOT_ANALYZED,
    /** The {@code MISSING_IMPLEMENTATION} constant. */
    MISSING_IMPLEMENTATION,
    /** The {@code CANCELLED} constant. */
    CANCELLED,
    /** The {@code BUDGET_EXCEEDED} constant. */
    BUDGET_EXCEEDED
}
