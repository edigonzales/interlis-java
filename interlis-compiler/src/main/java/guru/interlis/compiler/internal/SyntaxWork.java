package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.execution.CancellationToken;

/** Work charged to one parse or one source inventory. */
public final class SyntaxWork {
    private final AnalysisBudget limits;
    private final CancellationToken cancellation;
    private long tokens;
    private long nodes;
    private int recoveries;

    public SyntaxWork(AnalysisBudget limits, CancellationToken cancellation) {
        this.limits = limits;
        this.cancellation = cancellation;
    }

    public void token() {
        cancellation.throwIfCancellationRequested();
        tokens = charge("maxTokens", tokens, 1, limits.maxTokens());
    }

    public void node() {
        cancellation.throwIfCancellationRequested();
        nodes = charge("maxSyntaxNodes", nodes, 1, limits.maxSyntaxNodes());
    }

    public void recovery() {
        recoveries = (int) charge("maxRecoveryEvents", recoveries, 1, limits.maxRecoveryEvents());
    }

    public long nodes() { return nodes; }

    public int recoveries() { return recoveries; }

    public void reuse(long tokenCount, long nodeCount, int recoveryCount) {
        cancellation.throwIfCancellationRequested();
        tokens = charge("maxTokens", tokens, tokenCount, limits.maxTokens());
        nodes = charge("maxSyntaxNodes", nodes, nodeCount, limits.maxSyntaxNodes());
        recoveries = (int) charge("maxRecoveryEvents", recoveries, recoveryCount, limits.maxRecoveryEvents());
    }

    private static long charge(String resource, long used, long amount, long limit) {
        if (amount > limit - used) {
            throw new AnalysisBudgetExceededException(resource, limit);
        }
        return used + amount;
    }
}
