package guru.interlis.compiler;

/** Gate policy for warnings; it never changes the warning's severity. */
public enum WarningPolicy {
    /** The {@code ALLOW_WARNINGS} constant. */
    ALLOW_WARNINGS,
    /** The {@code REJECT_WARNINGS} constant. */
    REJECT_WARNINGS
}
