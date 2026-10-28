package guru.interlis.model;

/** Stable reason for an attempted but unsuccessful binding. */
public enum UnresolvedReason {
    /** The {@code NAME_NOT_FOUND} constant. */
    NAME_NOT_FOUND,
    /** The {@code AMBIGUOUS_NAME} constant. */
    AMBIGUOUS_NAME,
    /** The {@code NOT_A_TYPE} constant. */
    NOT_A_TYPE,
    /** A symbol was found but does not denote a measurement unit. */
    NOT_A_UNIT,
    /** The {@code UNKNOWN_ID} constant. */
    UNKNOWN_ID,
    /** The {@code REFERENCE_UNRESOLVED} constant. */
    REFERENCE_UNRESOLVED,
    /** The {@code OUTSIDE_SELECTION} constant. */
    OUTSIDE_SELECTION
}
