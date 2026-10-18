package guru.interlis.compiler.query;

import guru.interlis.model.SnapshotId;

import java.util.List;
import java.util.Objects;

/**
 * Ordered references with a completeness flag.
 *
 * @param references the {@code references} component
 * @param complete the {@code complete} component
 * @param snapshotId snapshot identifier
 */
public record ReferenceResult(List<ReferenceOccurrence> references, boolean complete,
                              SnapshotId snapshotId) {
    /** Creates a validated instance. */
    public ReferenceResult {
        references = List.copyOf(Objects.requireNonNull(references, "references"));
        Objects.requireNonNull(snapshotId, "snapshotId");
    }
}
