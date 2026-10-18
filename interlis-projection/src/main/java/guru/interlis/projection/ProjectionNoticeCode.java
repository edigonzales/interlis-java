package guru.interlis.projection;

/** Fine-grained projection finding, distinct from language diagnostics. */
public enum ProjectionNoticeCode {
    /** The {@code UNSUPPORTED_PRESENTATION} constant. */
    UNSUPPORTED_PRESENTATION,
    /** The {@code UNRESOLVED_TARGET} constant. */
    UNRESOLVED_TARGET,
    /** The {@code PROJECTION_LIMIT_REACHED} constant. */
    PROJECTION_LIMIT_REACHED,
    /** The {@code STALE_SELECTION} constant. */
    STALE_SELECTION,
    /** The {@code MISSING_MEMBER} constant. */
    MISSING_MEMBER
}
