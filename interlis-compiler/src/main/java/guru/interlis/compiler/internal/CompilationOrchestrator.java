package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.RunLifecycle;
import guru.interlis.syntax.ParseMode;

/** Coordinates one run and the gate without holding workspace state. */
final class CompilationOrchestrator {
    Result run(CompilationRequest request, AnalysisReuse reuse, ParseMode mode,
               CompilerServices services, CancellationToken token, RunLifecycle lifecycle) {
        CompilationRun run = new CompilationRun(request, reuse, services, token);
        RunOutput output = run.execute(mode, lifecycle);
        GateResult gate = new CompilationGate().evaluate(output, request, mode == ParseMode.STRICT);
        return new Result(output, gate);
    }

    record Result(RunOutput output, GateResult gate) {
    }
}
