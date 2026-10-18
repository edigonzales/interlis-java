package guru.interlis.model;

/** Semantic role of a name-reference occurrence. */
public enum ReferenceUsage {
    /** The {@code TYPE_USE} constant. */
    TYPE_USE,
    /** The {@code BASE_TYPE} constant. */
    BASE_TYPE,
    /** The {@code ROLE_TARGET} constant. */
    ROLE_TARGET,
    /** The {@code IMPORT} constant. */
    IMPORT,
    /** The {@code EXPRESSION} constant. */
    EXPRESSION,
    /** The {@code DECLARATION_END} constant. */
    DECLARATION_END
}
