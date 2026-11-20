package guru.interlis.workspace;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.workspace.resolution.PreparedClosure;

import java.util.Objects;

/** Frozen input state for one engine call; not a compiler certificate. */
public final class WorkspaceCapture {
    private final WorkspaceRevision workspaceRevision;
    private final CompilationRequest request;
    private final AnalysisReuse reuse;
    private final PreparedClosure resolution;
    private final Object origin;

    WorkspaceCapture(WorkspaceRevision workspaceRevision, CompilationRequest request,
                     AnalysisReuse reuse, PreparedClosure resolution,
                     Object origin) {
        this.workspaceRevision = Objects.requireNonNull(workspaceRevision, "workspaceRevision");
        this.request = Objects.requireNonNull(request, "request");
        this.reuse = Objects.requireNonNull(reuse, "reuse");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.origin = Objects.requireNonNull(origin, "origin");
    }

    /** Returns the pinned workspace revision. */
    public WorkspaceRevision workspaceRevision() {
        return workspaceRevision;
    }

    /** Returns the owned compilation request. */
    public CompilationRequest request() {
        return request;
    }

    /** Returns the reuse hints captured for this revision. */
    public AnalysisReuse reuse() {
        return reuse;
    }

    /** Returns the prepared import closure. */
    public PreparedClosure resolution() {
        return resolution;
    }

    /**
     * Returns the {@code origin} value.
     *
     * @return the value
     */
    Object origin() {
        return origin;
    }
}
