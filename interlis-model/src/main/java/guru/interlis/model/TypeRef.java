package guru.interlis.model;

import java.util.Objects;

/**
 * Public snapshot-bound type handle.
 *
 * @param snapshot snapshot identifier
 * @param type type reference
 */
public record TypeRef(SnapshotId snapshot, TypeId type) {
    /** Creates a validated instance. */
    public TypeRef {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(type, "type");
    }
}
