package guru.interlis.workspace;

/**
 * Limits on retained syntax for reuse across analyses.
 *
 * @param maxSyntaxEntries maximum number of syntax snapshots, zero disables retention
 * @param maxRetainedSourceUtf16Units maximum total source text length in UTF-16 units
 */
public record CacheLimits(int maxSyntaxEntries, long maxRetainedSourceUtf16Units) {
    /** Creates limits with non-negative bounds. */
    public CacheLimits {
        if (maxSyntaxEntries < 0 || maxRetainedSourceUtf16Units < 0) {
            throw new IllegalArgumentException("cache limits must be non-negative");
        }
    }

    /** Returns the default syntax retention limits. */
    public static CacheLimits defaults() {
        return new CacheLimits(128, 16_777_216L);
    }
}
