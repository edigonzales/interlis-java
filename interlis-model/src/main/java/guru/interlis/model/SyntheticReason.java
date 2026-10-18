package guru.interlis.model;

/** Reason for a synthetic, non-source element. */
public enum SyntheticReason {
    /** The {@code MISSING_TYPE} constant. */
    MISSING_TYPE,
    /** The {@code MISSING_REFERENCE} constant. */
    MISSING_REFERENCE,
    /** The {@code IMPLICIT_ELEMENT} constant. */
    IMPLICIT_ELEMENT,
    /** The {@code TEST_FIXTURE} constant. */
    TEST_FIXTURE,
    /** The {@code UNSUPPORTED_CONSTRUCT} constant. */
    UNSUPPORTED_CONSTRUCT
}
