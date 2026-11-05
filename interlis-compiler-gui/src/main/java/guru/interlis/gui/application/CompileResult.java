package guru.interlis.gui.application;

import guru.interlis.compiler.AnalysisSnapshot;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.workspace.resolution.PreparedClosure;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.RunEvent;
import guru.interlis.repository.ContentDigest;
import guru.interlis.repository.RepositoryResolutionOutcome;
import guru.interlis.repository.RepositorySearchStep;
import guru.interlis.repository.ResolutionLock;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A terminal result with the original source revisions and diagnostics for this request.
 * The prepared closure, when present, is the workspace preparation at the same revision.
 * The actual compiler outcome supplies the gate decision and request diagnostics.
 */
public record CompileResult(CompileRequest request, Instant completedAt, Duration elapsed, Outcome outcome,
                            Map<SourceId, SourceSnapshot> sources, Optional<PreparedClosure> preparedClosure,
                            Optional<ResolutionLock> repositoryLock, List<RepositorySearchStep> repositorySteps,
                            List<RunEvent> events) {
    public CompileResult {
        Objects.requireNonNull(request);
        Objects.requireNonNull(completedAt);
        Objects.requireNonNull(elapsed);
        Objects.requireNonNull(outcome);
        sources = Map.copyOf(sources);
        Objects.requireNonNull(preparedClosure);
        Objects.requireNonNull(repositoryLock);
        repositorySteps = List.copyOf(repositorySteps);
        events = List.copyOf(events);
    }

    public List<Diagnostic> diagnostics() {
        return switch (outcome) {
            case Compiled(var result) -> switch (result) {
                case CompilationOutcome.Succeeded(var schema, var analysis) -> analysis.requestDiagnostics();
                case CompilationOutcome.Rejected(var analysis, var rejection) -> analysis.requestDiagnostics();
                case CompilationOutcome.Cancelled(var analysis, var reason) ->
                        analysis.map(AnalysisSnapshot::requestDiagnostics).orElse(List.of());
                case CompilationOutcome.Failed(var failureId, var error) -> List.of(error);
            };
            case RepositoryRejected(var rejection) -> rejection.diagnostics();
            default -> List.of();
        };
    }

    public enum Origin { LOCAL_INPUT, REPOSITORY }

    /** Original text and revision. A byte digest is present only when available from the loader. */
    public record SourceSnapshot(SourceText source, Optional<ContentDigest> digest, Origin origin) {
        public SourceSnapshot {
            Objects.requireNonNull(source);
            Objects.requireNonNull(digest);
            Objects.requireNonNull(origin);
        }
    }

    public sealed interface Outcome permits Compiled, RepositoryRejected, InputFailed, ResolutionStale, Cancelled, Failed {
    }

    public record Compiled(CompilationOutcome result) implements Outcome {
        public Compiled {
            Objects.requireNonNull(result);
        }
    }

    public record RepositoryRejected(RepositoryResolutionOutcome.Rejected rejection) implements Outcome {
        public RepositoryRejected {
            Objects.requireNonNull(rejection);
        }
    }

    public record InputFailed(List<LocalInput.Problem> problems) implements Outcome {
        public InputFailed {
            problems = List.copyOf(problems);
        }
    }

    public record ResolutionStale() implements Outcome {
    }

    public record Cancelled(CancellationReason reason) implements Outcome {
        public Cancelled {
            Objects.requireNonNull(reason);
        }
    }

    public record Failed() implements Outcome {
    }
}
