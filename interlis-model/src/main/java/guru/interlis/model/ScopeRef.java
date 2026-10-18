package guru.interlis.model;

import java.util.Objects;

/**
 * Public snapshot-bound scope handle.
 *
 * @param snapshot snapshot identifier
 * @param scope the {@code scope} component
 */
public record ScopeRef(SnapshotId snapshot, ScopeId scope) {
    /** Creates a validated instance. */
    public ScopeRef {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(scope, "scope");
    }
}
