package guru.interlis.model;

/** Closed expression operators with explicit semantics. */
public enum BinaryOperator {
    /** The {@code EQ} constant. */
    EQ,
    /** The {@code NEQ} constant. */
    NEQ,
    /** The {@code LT} constant. */
    LT,
    /** The {@code LTE} constant. */
    LTE,
    /** The {@code GT} constant. */
    GT,
    /** The {@code GTE} constant. */
    GTE,
    /** The {@code AND} constant. */
    AND,
    /** The {@code OR} constant. */
    OR,
    /** The {@code PLUS} constant. */
    PLUS,
    /** The {@code MINUS} constant. */
    MINUS,
    /** The {@code MUL} constant. */
    MUL,
    /** The {@code DIV} constant. */
    DIV,
    /** The {@code POW} constant. */
    POW
}
