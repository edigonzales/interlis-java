package guru.interlis.repository;

import guru.interlis.execution.CancellationReason;
import guru.interlis.workspace.WorkspaceRevision;
import java.util.List;
import java.util.Objects;

/** A prepared conditional update is not a compiler success or schema certificate. */
public sealed interface RepositoryResolutionOutcome {
    record Prepared(PreparedRepositoryUpdate update) implements RepositoryResolutionOutcome {
        public Prepared { Objects.requireNonNull(update, "update"); }
    }
    record Rejected(List<RepositoryProblem> problems, List<guru.interlis.diagnostics.Diagnostic> diagnostics,
                    java.util.Map<guru.interlis.source.SourceId,guru.interlis.source.SourceText> sources,
                    List<RepositorySearchStep> steps) implements RepositoryResolutionOutcome {
        public Rejected(List<RepositoryProblem> problems) { this(problems,List.of(),java.util.Map.of(),List.of()); }
        public Rejected {
            diagnostics=List.copyOf(diagnostics); sources=java.util.Map.copyOf(sources); steps=List.copyOf(steps);
            problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
            if (problems.isEmpty()) throw new IllegalArgumentException("empty rejection");
        }
    }
    record Cancelled(CancellationReason reason) implements RepositoryResolutionOutcome {
        public Cancelled { Objects.requireNonNull(reason, "reason"); }
    }
    record Stale(WorkspaceRevision expected, WorkspaceRevision actual) implements RepositoryResolutionOutcome {
        public Stale { Objects.requireNonNull(expected, "expected"); Objects.requireNonNull(actual, "actual"); }
    }
}
