package guru.interlis.workspace;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Receipt of one accepted (or no-op) source update.
 *
 * @param source source identifier
 * @param previousSourceRevision the {@code previousSourceRevision} component
 * @param sourceRevision the {@code sourceRevision} component
 * @param previousWorkspaceRevision the {@code previousWorkspaceRevision} component
 * @param workspaceRevision the {@code workspaceRevision} component
 * @param changed the {@code changed} component
 * @param reasons rejection reasons
 */
public record UpdateReceipt(SourceId source, Optional<SourceRevision> previousSourceRevision,
                            Optional<SourceRevision> sourceRevision,
                            WorkspaceRevision previousWorkspaceRevision,
                            WorkspaceRevision workspaceRevision, boolean changed,
                            List<InvalidationReason> reasons) {
    /** Creates a validated instance. */
    public UpdateReceipt {
        Objects.requireNonNull(source, "source");
        previousSourceRevision = Objects.requireNonNull(previousSourceRevision,
                "previousSourceRevision");
        sourceRevision = Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(previousWorkspaceRevision, "previousWorkspaceRevision");
        Objects.requireNonNull(workspaceRevision, "workspaceRevision");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }
}
