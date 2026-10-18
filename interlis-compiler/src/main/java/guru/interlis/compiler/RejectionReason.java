package guru.interlis.compiler;

/** Reasons produced only by the compilation gate. */
public enum RejectionReason {
    /** The {@code SOURCE_ERRORS} constant. */
    SOURCE_ERRORS,
    /** The {@code WARNINGS_REJECTED} constant. */
    WARNINGS_REJECTED,
    /** The {@code MISSING_INPUT} constant. */
    MISSING_INPUT,
    /** The {@code AMBIGUOUS_INPUT} constant. */
    AMBIGUOUS_INPUT,
    /** The {@code ENVIRONMENT_FAILURE} constant. */
    ENVIRONMENT_FAILURE,
    /** The {@code INCOMPLETE_SUPPORT} constant. */
    INCOMPLETE_SUPPORT
}
