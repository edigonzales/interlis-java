package guru.interlis.model;

/** Type-checking state of an expression. */
public enum ExpressionTypeState {
    /** The {@code RESOLVED} constant. */
    RESOLVED,
    /** The {@code ERROR} constant. */
    ERROR,
    /** The {@code PENDING} constant. */
    PENDING
}
