package guru.interlis.workspace;

import java.util.List;
import java.util.Objects;

/**
 * Atomic receipt of several source updates.
 *
 * @param previousWorkspaceRevision the {@code previousWorkspaceRevision} component
 * @param workspaceRevision the {@code workspaceRevision} component
 * @param updates the {@code updates} component
 * @param reasons rejection reasons
 */
public record BatchUpdateReceipt(WorkspaceRevision previousWorkspaceRevision,
                                 WorkspaceRevision workspaceRevision,
                                 List<UpdateReceipt> updates,
                                 List<InvalidationReason> reasons) {
    /** Creates a validated instance. */
    public BatchUpdateReceipt {
        Objects.requireNonNull(previousWorkspaceRevision, "previousWorkspaceRevision");
        Objects.requireNonNull(workspaceRevision, "workspaceRevision");
        updates = List.copyOf(Objects.requireNonNull(updates, "updates"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }
}
