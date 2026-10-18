package guru.interlis.workspace.resolution;

import guru.interlis.compiler.SourceSet;

import java.util.List;

/** Common prepared closure contract, complete or explicitly incomplete. */
public sealed interface PreparedClosure permits ResolvedClosure, IncompleteClosure {
    /**
     * Returns the {@code sourceSet} value.
     *
     * @return the value
     */
    SourceSet sourceSet();

    /**
     * Returns the {@code pendingFetches} value.
     *
     * @return the value
     */
    List<PendingSourceRequest> pendingFetches();

    /**
     * Returns the {@code trace} value.
     *
     * @return the value
     */
    ResolutionTrace trace();

    /**
     * Returns the {@code dependencies} value.
     *
     * @return the value
     */
    List<ResolutionDependency> dependencies();

    /**
     * Returns the {@code negativeDependencies} value.
     *
     * @return the value
     */
    List<ResolutionDependency> negativeDependencies();

    /**
     * Returns the {@code complete} value.
     *
     * @return the value
     */
    boolean complete();
}
