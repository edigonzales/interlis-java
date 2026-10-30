package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngine;
import guru.interlis.compiler.CompilerInternalException;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.compiler.RejectionSummary;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticIds;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.FailureId;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.execution.RunCompletion;
import guru.interlis.execution.RunContext;
import guru.interlis.execution.RunLifecycle;
import guru.interlis.syntax.ParseMode;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Default reentrant compiler engine. */
public final class DefaultCompilerEngine implements CompilerEngine {
    private static long failures;
    private final CompilerServices services;
    private final CompilationOrchestrator orchestrator = new CompilationOrchestrator();

    public DefaultCompilerEngine(CompilerServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public AnalysisSnapshot analyze(CompilationRequest request, CancellationToken cancellation) {
        return analyze(request, AnalysisReuse.none(), cancellation);
    }

    @Override
    public AnalysisSnapshot analyze(CompilationRequest request, AnalysisReuse reuse,
                                    CancellationToken cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reuse, "reuse");
        Objects.requireNonNull(cancellation, "cancellation");
        return analyze(request, reuse, services.runContexts().createRoot(), cancellation);
    }

    @Override
    public AnalysisSnapshot analyze(CompilationRequest request, AnalysisReuse reuse,
                                    RunContext context, CancellationToken cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reuse, "reuse");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(cancellation, "cancellation");
        RunLifecycle lifecycle = RunLifecycle.create(context, services.observer(), services.clock());
        lifecycle.start(metadata(request, "analyze"));
        try {
            CompilationOrchestrator.Result result = orchestrator.run(request, reuse,
                    ParseMode.EDITOR, services, cancellation, lifecycle);
            AnalysisSnapshot snapshot = result.output().analysis();
            lifecycle.finish(completion(result));
            return snapshot;
        } catch (OperationCancelledException cancelled) {
            lifecycle.finish(new RunCompletion.Cancelled(cancelled.reason(), Optional.empty(), 0));
            throw cancelled;
        } catch (AnalysisBudgetExceededException budget) {
            OperationCancelledException cancelled = new OperationCancelledException(
                    CancellationReason.RESOURCE_LIMIT);
            lifecycle.finish(new RunCompletion.Cancelled(cancelled.reason(), Optional.empty(), 0));
            throw cancelled;
        } catch (CompilerInternalException internal) {
            lifecycle.finish(new RunCompletion.Failed(internal.failureId(), 1));
            throw internal;
        } catch (RuntimeException failure) {
            CompilerInternalException internal = internal(failure);
            lifecycle.finish(new RunCompletion.Failed(internal.failureId(), 1));
            throw internal;
        }
    }

    @Override
    public CompilationOutcome compile(CompilationRequest request, CancellationToken cancellation) {
        return compile(request, AnalysisReuse.none(), cancellation);
    }

    @Override
    public CompilationOutcome compile(CompilationRequest request, AnalysisReuse reuse,
                                      CancellationToken cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reuse, "reuse");
        Objects.requireNonNull(cancellation, "cancellation");
        return compile(request, reuse, services.runContexts().createRoot(), cancellation);
    }

    @Override
    public CompilationOutcome compile(CompilationRequest request, AnalysisReuse reuse,
                                      RunContext context, CancellationToken cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reuse, "reuse");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(cancellation, "cancellation");
        RunLifecycle lifecycle = RunLifecycle.create(context, services.observer(), services.clock());
        lifecycle.start(metadata(request, "compile"));
        try {
            CompilationOrchestrator.Result result = orchestrator.run(request, reuse,
                    ParseMode.STRICT, services, cancellation, lifecycle);
            AnalysisSnapshot analysis = result.output().analysis();
            if (result.gate().schema().isPresent()) {
                var schema = result.gate().schema().get();
                lifecycle.finish(new RunCompletion.Succeeded(
                        (int) analysis.diagnostics().stream()
                                .filter(d -> d.severity() == Severity.ERROR).count(),
                        result.output().graphResult().roots().size(), true));
                return new CompilationOutcome.Succeeded(schema, analysis);
            }
            RejectionSummary summary = result.gate().rejection().orElseThrow();
            lifecycle.finish(new RunCompletion.Rejected(
                    summary.reasons().stream().map(Enum::name)
                            .collect(java.util.stream.Collectors.toCollection(
                                    java.util.LinkedHashSet::new)),
                    summary.diagnosticIds().stream().map(DiagnosticId::value).toList(),
                    summary.unsupportedFeatureIds(),
                    (int) analysis.diagnostics().stream()
                            .filter(d -> d.severity() == Severity.ERROR).count(), false));
            return new CompilationOutcome.Rejected(analysis, summary);
        } catch (OperationCancelledException cancelled) {
            lifecycle.finish(new RunCompletion.Cancelled(cancelled.reason(), Optional.empty(), 0));
            return new CompilationOutcome.Cancelled(Optional.empty(), cancelled.reason());
        } catch (AnalysisBudgetExceededException budget) {
            lifecycle.finish(new RunCompletion.Cancelled(CancellationReason.RESOURCE_LIMIT,
                    Optional.empty(), 0));
            return new CompilationOutcome.Cancelled(Optional.empty(),
                    CancellationReason.RESOURCE_LIMIT);
        } catch (CompilerInternalException internal) {
            lifecycle.finish(new RunCompletion.Failed(internal.failureId(), 1));
            return new CompilationOutcome.Failed(internal.failureId(), internal.safeDiagnostic());
        } catch (RuntimeException failure) {
            CompilerInternalException internal = internal(failure);
            lifecycle.finish(new RunCompletion.Failed(internal.failureId(), 1));
            return new CompilationOutcome.Failed(internal.failureId(), internal.safeDiagnostic());
        }
    }

    private RunCompletion completion(CompilationOrchestrator.Result result) {
        AnalysisSnapshot analysis = result.output().analysis();
        int errors = (int) analysis.diagnostics().stream()
                .filter(d -> d.severity() == Severity.ERROR).count();
        if (result.gate().schema().isPresent()) {
            return new RunCompletion.Succeeded(errors,
                    result.output().graphResult().roots().size(), true, false);
        }
        RejectionSummary summary = result.gate().rejection().orElseThrow();
        return new RunCompletion.Rejected(summary.reasons().stream().map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
                summary.diagnosticIds().stream().map(DiagnosticId::value).toList(),
                summary.unsupportedFeatureIds(), errors, false);
    }

    private static guru.interlis.execution.RunEvent.RunMetadata metadata(
            CompilationRequest request, String operation) {
        return new guru.interlis.execution.RunEvent.RunMetadata(operation,
                request.options().allowedLanguages().stream()
                        .map(guru.interlis.model.LanguageVersion::literal).sorted().toList(),
                request.roots().size(), request.options().fingerprint());
    }

    private static synchronized long nextFailure() {
        return ++failures;
    }

    private static CompilerInternalException internal(RuntimeException failure) {
        FailureId failureId = new FailureId("failure-" + nextFailure());
        DiagnosticId diagnosticId = DiagnosticIds.create(new DiagnosticCode("ILI-INF-0001"),
                Phase.INFRASTRUCTURE, Optional.empty(), "diagnostic.internal",
                List.of(new DiagnosticArgument.TextArgument("failureId", failureId.value())));
        Diagnostic diagnostic = new Diagnostic(diagnosticId, new DiagnosticCode("ILI-INF-0001"),
                Severity.ERROR, Phase.INFRASTRUCTURE, "diagnostic.internal",
                List.of(new DiagnosticArgument.TextArgument("failureId", failureId.value())),
                Optional.empty(), List.of(), Optional.empty(), List.of(), Optional.empty(),
                Optional.of(failureId), List.of());
        return new CompilerInternalException(failureId, diagnostic, failure);
    }
}
