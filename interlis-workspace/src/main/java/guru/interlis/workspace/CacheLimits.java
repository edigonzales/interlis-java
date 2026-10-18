package guru.interlis.workspace;

/**
 * Cache limits are operating settings, never semantic settings.
 *
 * @param maxSyntaxEntries the {@code maxSyntaxEntries} component
 * @param maxAnalysisEntries the {@code maxAnalysisEntries} component
 * @param maxQueryEntries the {@code maxQueryEntries} component
 * @param maxRetainedSourceUtf16Units the {@code maxRetainedSourceUtf16Units} component
 */
public record CacheLimits(int maxSyntaxEntries, int maxAnalysisEntries,
                          int maxQueryEntries, long maxRetainedSourceUtf16Units) {
    /** Creates a validated instance. */
    public CacheLimits {
        if (maxSyntaxEntries < 0 || maxAnalysisEntries < 0 || maxQueryEntries < 0
                || maxRetainedSourceUtf16Units < 0) {
            throw new IllegalArgumentException("cache limits must be non-negative");
        }
    }

    /**
     * Returns the {@code defaults} value.
     *
     * @return the value
     */
    public static CacheLimits defaults() {
        return new CacheLimits(128, 4, 1024, 16_777_216L);
    }
}
