package guru.interlis.workspace;

import guru.interlis.compiler.ModelSelector;

import java.util.List;
import java.util.Objects;

/**
 * Request for tolerant EDITOR analysis at a pinned workspace revision.
 *
 * @param expectedWorkspaceRevision the {@code expectedWorkspaceRevision} component
 * @param roots the {@code roots} component
 */
public record AnalysisRequest(WorkspaceRevision expectedWorkspaceRevision,
                              List<ModelSelector> roots) {
    /** Creates a validated instance. */
    public AnalysisRequest {
        Objects.requireNonNull(expectedWorkspaceRevision, "expectedWorkspaceRevision");
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root is required");
        }
    }
}
