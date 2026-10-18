package guru.interlis.model;

/** Coverage state per source and phase. */
public enum CoverageStatus {
    /** The {@code COMPLETE} constant. */
    COMPLETE,
    /** The {@code BLOCKED_BY_ERROR} constant. */
    BLOCKED_BY_ERROR,
    /** The {@code NOT_IMPLEMENTED} constant. */
    NOT_IMPLEMENTED,
    /** The {@code NOT_REQUESTED} constant. */
    NOT_REQUESTED,
    /** The {@code CANCELLED} constant. */
    CANCELLED,
    /** The {@code BUDGET_EXCEEDED} constant. */
    BUDGET_EXCEEDED
}
