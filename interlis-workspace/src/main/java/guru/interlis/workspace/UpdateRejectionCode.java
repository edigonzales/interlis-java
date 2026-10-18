package guru.interlis.workspace;

/** Stable API-level update rejection code. */
public enum UpdateRejectionCode {
    /** The {@code SOURCE_ALREADY_PRESENT} constant. */
    SOURCE_ALREADY_PRESENT,
    /** The {@code SOURCE_MISSING} constant. */
    SOURCE_MISSING,
    /** The {@code VERSION_CONFLICT} constant. */
    VERSION_CONFLICT,
    /** The {@code INVALID_EDIT} constant. */
    INVALID_EDIT,
    /** The {@code SOURCE_LIMIT_EXCEEDED} constant. */
    SOURCE_LIMIT_EXCEEDED,
    /** The {@code REVISION_EXHAUSTED} constant. */
    REVISION_EXHAUSTED,
    /** The {@code WORKSPACE_CLOSED} constant. */
    WORKSPACE_CLOSED,
    /** The {@code INVALID_CONFIGURATION} constant. */
    INVALID_CONFIGURATION
}
