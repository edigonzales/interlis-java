package guru.interlis.repository;

import guru.interlis.workspace.*;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, workspace-owned download batch. No IO is performed by apply; sources
 * and selection configuration are installed atomically at the captured revision.
 * A stale/foreign application is rejected without any mutation. The workspace
 * assigns final source revisions; provisional staging revisions never escape.
 */
public final class PreparedRepositoryUpdate {
    private final Workspace owner;
    private final WorkspaceRevision expectedRevision;
    private final String expectedConfigurationFingerprint;
    private final List<SourceUpdate> sources;
    private final WorkspaceConfiguration configuration;
    private final ResolutionLock lock;
    private final List<String> trace;
    PreparedRepositoryUpdate(Workspace owner, WorkspaceRevision expectedRevision, List<SourceUpdate> sources,
                             WorkspaceConfiguration configuration, String expectedConfigurationFingerprint, ResolutionLock lock, List<String> trace) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.expectedRevision = Objects.requireNonNull(expectedRevision, "expectedRevision");
        this.expectedConfigurationFingerprint = Objects.requireNonNull(expectedConfigurationFingerprint, "expectedConfigurationFingerprint");
        this.sources = List.copyOf(sources); this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.lock = Objects.requireNonNull(lock, "lock"); this.trace = List.copyOf(trace);
    }
    public WorkspaceRevision expectedRevision() { return expectedRevision; }
    public List<SourceUpdate> sources() { return sources; }
    public WorkspaceConfiguration configuration() { return configuration; }
    public ResolutionLock lock() { return lock; }
    /** Credential-free explanations; source identities remain exact in lock and updates. */
    public List<String> trace() { return trace; }
    public BatchUpdateReceipt apply(Workspace workspace) {
        if (workspace != owner) throw new IllegalArgumentException("update belongs to another workspace");
        return workspace.applyBatch(sources, expectedRevision, configuration, expectedConfigurationFingerprint);
    }
}
