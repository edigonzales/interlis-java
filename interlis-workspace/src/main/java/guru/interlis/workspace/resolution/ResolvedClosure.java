package guru.interlis.workspace.resolution;

import guru.interlis.compiler.SourceSet;
import guru.interlis.compiler.ModelLocation;

import java.util.List;
import java.util.Objects;

/**
 * Closure with all required sources and bindings present.
 *
 * @param sourceSet the {@code sourceSet} component
 * @param trace the {@code trace} component
 * @param dependencies the {@code dependencies} component
 * @param rootLocations selected root declarations in request order
 */
public record ResolvedClosure(SourceSet sourceSet, ResolutionTrace trace,
                              List<ResolutionDependency> dependencies,
                              List<ModelLocation> rootLocations) implements PreparedClosure {
    public ResolvedClosure(SourceSet sourceSet, ResolutionTrace trace, List<ResolutionDependency> dependencies) {
        this(sourceSet, trace, dependencies, List.of());
    }

    /** Creates a validated instance. */
    public ResolvedClosure {
        Objects.requireNonNull(sourceSet, "sourceSet");
        Objects.requireNonNull(trace, "trace");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        rootLocations = List.copyOf(rootLocations);
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
