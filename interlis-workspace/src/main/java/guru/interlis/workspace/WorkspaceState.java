package guru.interlis.workspace;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of workspace metadata.
 *
 * @param revision source revision
 * @param sourceRevisions the {@code sourceRevisions} component
 * @param configurationFingerprint the {@code configurationFingerprint} component
 * @param closed the {@code closed} component
 */
public record WorkspaceState(WorkspaceRevision revision,
                             Map<SourceId, SourceRevision> sourceRevisions,
                             String configurationFingerprint, boolean closed) {
    /** Creates a validated instance. */
    public WorkspaceState {
        Objects.requireNonNull(revision, "revision");
        sourceRevisions = Map.copyOf(Objects.requireNonNull(sourceRevisions, "sourceRevisions"));
        Objects.requireNonNull(configurationFingerprint, "configurationFingerprint");
    }
}
