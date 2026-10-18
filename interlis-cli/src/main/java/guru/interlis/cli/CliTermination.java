package guru.interlis.cli;

/** Stable in-process CLI classification; not a claim about legacy exit codes. */
public enum CliTermination {
    /** The {@code SUCCESS} constant. */
    SUCCESS,
    /** The {@code SOURCE_REJECTED} constant. */
    SOURCE_REJECTED,
    /** The {@code USAGE_ERROR} constant. */
    USAGE_ERROR,
    /** The {@code OPERATIONAL_FAILURE} constant. */
    OPERATIONAL_FAILURE,
    /** The {@code INCOMPLETE_ANALYSIS} constant. */
    INCOMPLETE_ANALYSIS,
    /** The {@code INPUT_UNRESOLVED} constant. */
    INPUT_UNRESOLVED,
    /** The {@code WARNINGS_REJECTED} constant. */
    WARNINGS_REJECTED,
    /** The {@code CANCELLED} constant. */
    CANCELLED,
    /** The {@code INTERNAL_FAILURE} constant. */
    INTERNAL_FAILURE
}
