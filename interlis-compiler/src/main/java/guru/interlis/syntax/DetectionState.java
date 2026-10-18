package guru.interlis.syntax;

/** State of language-version detection. */
public enum DetectionState {
    /** The {@code DETECTED} constant. */
    DETECTED,
    /** The {@code ABSENT} constant. */
    ABSENT,
    /** The {@code UNSUPPORTED} constant. */
    UNSUPPORTED,
    /** The {@code ASSUMED_FOR_RECOVERY} constant. */
    ASSUMED_FOR_RECOVERY
}
