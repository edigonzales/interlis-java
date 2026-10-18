package guru.interlis.compiler;

/** Resolver failure reason, separate from a compiler language error. */
public enum ResolutionReason {
    /** The {@code NOT_FOUND} constant. */
    NOT_FOUND,
    /** The {@code AMBIGUOUS} constant. */
    AMBIGUOUS,
    /** The {@code OFFLINE} constant. */
    OFFLINE,
    /** The {@code ACCESS_DENIED} constant. */
    ACCESS_DENIED,
    /** The {@code IO_FAILURE} constant. */
    IO_FAILURE,
    /** The {@code INVALID_SOURCE} constant. */
    INVALID_SOURCE,
    /** The {@code VERSION_MISMATCH} constant. */
    VERSION_MISMATCH,
    /** The {@code STALE_SOURCE} constant. */
    STALE_SOURCE,
    /** The {@code BUDGET_EXCEEDED} constant. */
    BUDGET_EXCEEDED
}
