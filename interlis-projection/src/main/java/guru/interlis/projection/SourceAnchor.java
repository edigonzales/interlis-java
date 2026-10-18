package guru.interlis.projection;

import guru.interlis.model.SnapshotId;
import guru.interlis.source.SourceRange;

import java.util.Objects;
import java.util.Optional;

/**
 * Snapshot-bound source navigation anchor.
 *
 * @param snapshotId snapshot identifier
 * @param nameRange the {@code nameRange} component
 * @param declarationRange the {@code declarationRange} component
 */
public record SourceAnchor(SnapshotId snapshotId, SourceRange nameRange,
                           Optional<SourceRange> declarationRange) {
    /** Creates a validated instance. */
    public SourceAnchor {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(nameRange, "nameRange");
        declarationRange = Objects.requireNonNull(declarationRange, "declarationRange");
    }
}
