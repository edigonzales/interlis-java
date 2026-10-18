package guru.interlis.workspace;

import java.util.List;
import java.util.Objects;

/**
 * Receipt for a workspace configuration change.
 *
 * @param previousWorkspaceRevision the {@code previousWorkspaceRevision} component
 * @param workspaceRevision the {@code workspaceRevision} component
 * @param previousConfigurationFingerprint the {@code previousConfigurationFingerprint} component
 * @param configurationFingerprint the {@code configurationFingerprint} component
 * @param changed the {@code changed} component
 * @param reasons rejection reasons
 */
public record ConfigurationReceipt(WorkspaceRevision previousWorkspaceRevision,
                                   WorkspaceRevision workspaceRevision,
                                   String previousConfigurationFingerprint,
                                   String configurationFingerprint, boolean changed,
                                   List<InvalidationReason> reasons) {
    /** Creates a validated instance. */
    public ConfigurationReceipt {
        Objects.requireNonNull(previousWorkspaceRevision, "previousWorkspaceRevision");
        Objects.requireNonNull(workspaceRevision, "workspaceRevision");
        Objects.requireNonNull(previousConfigurationFingerprint,
                "previousConfigurationFingerprint");
        Objects.requireNonNull(configurationFingerprint, "configurationFingerprint");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }
}
