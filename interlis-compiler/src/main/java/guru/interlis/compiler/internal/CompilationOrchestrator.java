package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunLifecycle;
import guru.interlis.syntax.ParseMode;

import java.util.Objects;
import java.util.function.Predicate;

/** Coordinates one run and the gate without holding workspace state. */
final class CompilationOrchestrator {
    private final DefaultCompilerEngine.SnapshotFactory snapshotFactory;
    private final Predicate<AnalysisSnapshot> compilerProduced;

    CompilationOrchestrator(DefaultCompilerEngine.SnapshotFactory snapshotFactory, Predicate<AnalysisSnapshot> compilerProduced) {
        this.snapshotFactory = Objects.requireNonNull(snapshotFactory, "snapshotFactory");
        this.compilerProduced = Objects.requireNonNull(compilerProduced, "compilerProduced");
    }

    Result run(CompilationRequest request, AnalysisReuse reuse, ParseMode mode,
               CancellationToken token, RunLifecycle lifecycle) {
        var previous = reuse.previousAnalysis().filter(compilerProduced);
        CompilationRun run = new CompilationRun(request, reuse.previousSyntax(), previous, snapshotFactory, token);
        RunOutput output = run.execute(mode, lifecycle);
        GateResult gate = new CompilationGate().evaluate(output, request, mode == ParseMode.STRICT);
        return new Result(output, gate);
    }

    record Result(RunOutput output, GateResult gate) {
    }
}
