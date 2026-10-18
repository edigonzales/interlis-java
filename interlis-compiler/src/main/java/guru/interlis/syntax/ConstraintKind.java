package guru.interlis.syntax;

/** Constraint kinds recognized by the syntax layer. */
public enum ConstraintKind {
    /** The {@code MANDATORY} constant. */
    MANDATORY,
    /** The {@code PLAUSIBILITY} constant. */
    PLAUSIBILITY,
    /** The {@code EXISTENCE} constant. */
    EXISTENCE,
    /** The {@code UNIQUENESS} constant. */
    UNIQUENESS,
    /** The {@code SET} constant. */
    SET,
    /** The {@code DOMAIN} constant. */
    DOMAIN
}
