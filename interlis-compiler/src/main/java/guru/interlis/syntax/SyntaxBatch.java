package guru.interlis.syntax;

import guru.interlis.compiler.internal.AnalysisBudgetExceededException;
import guru.interlis.compiler.internal.SyntaxWork;
import guru.interlis.compiler.internal.antlr.AntlrSyntaxService;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.source.SourceText;

import java.util.Objects;

/**
 * Syntax preparation for one source inventory with shared token, node and recovery
 * limits. A batch is mutable, single-use for an inventory and confined to one caller.
 */
public final class SyntaxBatch {
    private final ParseOptions options;
    private final CancellationToken cancellation;
    private final SyntaxWork work;
    private final AntlrSyntaxService parser = new AntlrSyntaxService();

    SyntaxBatch(ParseOptions options, CancellationToken cancellation) {
        this.options = Objects.requireNonNull(options, "options");
        this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
        this.work = new SyntaxWork(options.budget(), cancellation);
    }

    /**
     * Prepares the next non-null source, optionally using a snapshot of the exact
     * same source and parse profile. The returned value is immutable.
     * @param previous reuse hint, or null
     * @throws OperationCancelledException if cancellation is requested or a limit is reached
     */
    public SyntaxSnapshot prepare(SourceText source, SyntaxSnapshot previous) {
        cancellation.throwIfCancellationRequested();
        try {
            if (previous != null && source.equals(previous.source())
                    && previous.options().equals(options) && previous.usage().isPresent()
                    && !previous.coverage().budgetExceeded() && !previous.coverage().cancelled()) {
                var usage = previous.usage().orElseThrow();
                work.reuse(previous.tokens().size(), usage.nodes(), usage.recoveryEvents());
                return previous;
            }
            var result = parser.parseDocument(source, options, cancellation, work);
            if (result.coverage().budgetExceeded()) {
                throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
            }
            return result;
        } catch (AnalysisBudgetExceededException limit) {
            throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
        }
    }
}
