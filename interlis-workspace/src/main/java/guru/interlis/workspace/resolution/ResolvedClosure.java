package guru.interlis.workspace.resolution;

import guru.interlis.compiler.SourceSet;

import java.util.List;
import java.util.Objects;

/**
 * Closure with all required sources and bindings present.
 *
 * @param sourceSet the {@code sourceSet} component
 * @param trace the {@code trace} component
 * @param dependencies the {@code dependencies} component
 */
public record ResolvedClosure(SourceSet sourceSet, ResolutionTrace trace,
                              List<ResolutionDependency> dependencies) implements PreparedClosure {
    /** Creates a validated instance. */
    public ResolvedClosure {
        Objects.requireNonNull(sourceSet, "sourceSet");
        Objects.requireNonNull(trace, "trace");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }

    @Override
    public List<PendingSourceRequest> pendingFetches() {
        return List.of();
    }

    @Override
    public List<ResolutionDependency> negativeDependencies() {
        return List.of();
    }

    @Override
    public boolean complete() {
        return true;
    }
}
