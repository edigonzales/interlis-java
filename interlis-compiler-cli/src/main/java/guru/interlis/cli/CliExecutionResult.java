package guru.interlis.cli;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.execution.RunId;
import guru.interlis.compiler.RejectionSummary;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Complete in-process CLI result. */
public record CliExecutionResult(int exitCode, CliTermination termination,
                                 List<Diagnostic> diagnostics, String output,
                                 Optional<RunId> runId, List<Artifact> artifacts,
                                 Optional<RejectionSummary> rejectionSummary) {
    /** Creates a validated instance. */
    public CliExecutionResult {
        Objects.requireNonNull(termination, "termination");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(output, "output");
        runId = Objects.requireNonNull(runId, "runId");
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
        rejectionSummary = Objects.requireNonNull(rejectionSummary, "rejectionSummary");
    }

    /** Constructs a result not yet attached to an invocation lifecycle. */
    public CliExecutionResult(int exitCode, CliTermination termination,
                              List<Diagnostic> diagnostics, String output) {
        this(exitCode, termination, diagnostics, output, Optional.empty(), List.of(), Optional.empty());
    }

    public CliExecutionResult(int exitCode, CliTermination termination, List<Diagnostic> diagnostics,
                              String output, Optional<RunId> runId) {
        this(exitCode, termination, diagnostics, output, runId, List.of(), Optional.empty());
    }

    /** Constructs a host result with completed artifacts and no compiler rejection. */
    public CliExecutionResult(int exitCode, CliTermination termination, List<Diagnostic> diagnostics,
                              String output, Optional<RunId> runId, List<Artifact> artifacts) {
        this(exitCode, termination, diagnostics, output, runId, artifacts, Optional.empty());
    }

    public enum ArtifactKind { FORMATTED_SOURCE, RESOLUTION_LOCK }

    /** Completed file replacements remain listed even if a later file fails. */
    public record Artifact(ArtifactKind kind, java.net.URI location, long byteCount) {
        public Artifact {
            Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(location, "location");
            if (!location.isAbsolute() || byteCount < 0) throw new IllegalArgumentException("invalid artifact information");
        }
    }
}
