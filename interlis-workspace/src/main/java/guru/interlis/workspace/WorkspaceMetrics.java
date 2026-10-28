package guru.interlis.workspace;

/**
 * Immutable workspace counters; durations belong to the host observer.
 *
 * @param parses the {@code parses} component
 * @param syntaxReuses the {@code syntaxReuses} component
 * @param semanticAnalyses engine calls initiated by this workspace, including failed/cancelled calls;
 *                         detached host calls are outside this counter
 * @param acceptedAnalyses successful acceptance operations, including detached host results
 * @param staleAnalyses the {@code staleAnalyses} component
 * @param cachedSyntaxEntries the {@code cachedSyntaxEntries} component
 * @param cachedAnalysisEntries the {@code cachedAnalysisEntries} component
 * @param evictions the {@code evictions} component
 * @param cancellations the {@code cancellations} component
 */
public record WorkspaceMetrics(long parses, long syntaxReuses, long semanticAnalyses,
                               long acceptedAnalyses, long staleAnalyses,
                               int cachedSyntaxEntries, int cachedAnalysisEntries,
                               long evictions, long cancellations) {
    /** Creates a validated instance. */
    public WorkspaceMetrics {
        if (parses < 0 || syntaxReuses < 0 || semanticAnalyses < 0 || acceptedAnalyses < 0
                || staleAnalyses < 0 || cachedSyntaxEntries < 0 || cachedAnalysisEntries < 0
                || evictions < 0 || cancellations < 0) {
            throw new IllegalArgumentException("metrics must be non-negative");
        }
    }
}
