package guru.interlis.model;

/** Context of an assignability check. */
public enum AssignmentContext {
    /** The {@code NORMAL} constant. */
    NORMAL,
    /** The {@code ATTRIBUTE_OVERRIDE} constant. */
    ATTRIBUTE_OVERRIDE,
    /** The {@code DOMAIN_EXTENSION} constant. */
    DOMAIN_EXTENSION,
    /** The {@code FUNCTION_ARGUMENT} constant. */
    FUNCTION_ARGUMENT
}
