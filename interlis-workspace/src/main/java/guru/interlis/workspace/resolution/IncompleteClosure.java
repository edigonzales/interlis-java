package guru.interlis.workspace.resolution;

import guru.interlis.compiler.SourceSet;

import java.util.List;
import java.util.Objects;

/**
 * Partial closure with explicit host-fetch requests.
 *
 * @param partialSourceSet the {@code partialSourceSet} component
 * @param pendingFetches the {@code pendingFetches} component
 * @param trace the {@code trace} component
 * @param dependencies the {@code dependencies} component
 * @param negativeDependencies the {@code negativeDependencies} component
 */
public record IncompleteClosure(SourceSet partialSourceSet, List<PendingSourceRequest> pendingFetches,
                                ResolutionTrace trace, List<ResolutionDependency> dependencies,
                                List<ResolutionDependency> negativeDependencies)
        implements PreparedClosure {
    /** Creates a validated instance. */
    public IncompleteClosure {
        Objects.requireNonNull(partialSourceSet, "partialSourceSet");
        pendingFetches = List.copyOf(Objects.requireNonNull(pendingFetches, "pendingFetches"));
        Objects.requireNonNull(trace, "trace");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        negativeDependencies = List.copyOf(
                Objects.requireNonNull(negativeDependencies, "negativeDependencies"));
    }

    @Override
    public SourceSet sourceSet() {
        return partialSourceSet;
    }

    @Override
    public boolean complete() {
        return false;
    }
}
