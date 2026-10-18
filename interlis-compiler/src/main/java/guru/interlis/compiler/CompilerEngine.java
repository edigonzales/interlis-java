package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunContext;

/** Reentrant compiler entry; every call owns its run-local mutable state. */
public interface CompilerEngine {
    /**
     * Analyzes a request without reuse and returns an immutable snapshot.
     *
     * @param request request whose collections are already owned by the request
     * @param cancellation cancellation signal, never {@code null}
     * @return immutable analysis snapshot
     * @throws guru.interlis.execution.OperationCancelledException if cancellation is observed
     * @throws guru.interlis.compiler.CompilerInternalException on an internal defect
     */
    AnalysisSnapshot analyze(CompilationRequest request, CancellationToken cancellation);

    /**
     * Analyzes with explicit previous-analysis reuse; stale reuse is ignored safely.
     *
     * @param request current request
     * @param reuse previous-analysis hints, never {@code null}
     * @param cancellation cancellation signal
     * @return immutable analysis snapshot
     */
    AnalysisSnapshot analyze(CompilationRequest request, AnalysisReuse reuse,
                             CancellationToken cancellation);

    /**
     * Analyzes with explicit reuse and run identity/observer propagation.
     *
     * @param request current request
     * @param reuse previous-analysis hints
     * @param context run identity and observer
     * @param cancellation cancellation signal
     * @return immutable analysis snapshot
     */
    AnalysisSnapshot analyze(CompilationRequest request, AnalysisReuse reuse,
                             RunContext context, CancellationToken cancellation);

    /**
     * Compiles a request through the closure gate.
     *
     * <p>A {@code Succeeded} result contains exactly the requested closure. Rejected,
     * cancelled and failed results never expose a {@code CompiledSchema}.</p>
     *
     * @param request request to compile
     * @param cancellation cancellation signal
     * @return closed compilation outcome
     */
    CompilationOutcome compile(CompilationRequest request, CancellationToken cancellation);

    /**
     * Compiles with explicit previous-analysis reuse.
     *
     * @param request current request
     * @param reuse previous-analysis hints
     * @param cancellation cancellation signal
     * @return closed compilation outcome
     */
    CompilationOutcome compile(CompilationRequest request, AnalysisReuse reuse,
                               CancellationToken cancellation);

    /**
     * Compiles with explicit reuse and run identity/observer propagation.
     *
     * @param request current request
     * @param reuse previous-analysis hints
     * @param context run identity and observer
     * @param cancellation cancellation signal
     * @return closed compilation outcome
     */
    CompilationOutcome compile(CompilationRequest request, AnalysisReuse reuse,
                               RunContext context, CancellationToken cancellation);
}
