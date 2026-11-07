package guru.interlis.transfer;

import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.model.SymbolRef;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

/** One immutable, owned object and its already constructed fields. Values can be retained
 * after advancing a reader. The object holds only its own data and any immutable shared
 * subtrees. Adapters choose input depth and size limits before constructing an object.
 * Snapshot checks walk nested containers iteratively. Constructors take defensive copies
 * of their inputs, so the value graph can be assembled from existing values without cycles.
 *
 * @param identity the identity and provenance of this occurrence
 * @param data object type and supplied values from one snapshot
 */
public record TransferObject(TransferIdentity identity, TransferValue.Structured data) {
    public TransferObject {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(data, "data");
        requireSnapshot(data);
    }

    /** Returns the snapshot-bound object type. */
    public SymbolRef type() {
        return data.type();
    }

    /** Returns a supplied value or empty for an absent slot. */
    public Optional<TransferValue> value(SymbolRef slot) {
        return data.value(slot);
    }

    private static void requireSnapshot(TransferValue.Structured data) {
        var snapshot = data.type().snapshot();
        var seen = Collections.newSetFromMap(new IdentityHashMap<TransferValue, Boolean>());
        var pending = new ArrayDeque<Iterator<? extends TransferValue>>();
        pending.push(data.values().values().iterator());
        while (!pending.isEmpty()) {
            var fields = pending.peek();
            if (!fields.hasNext()) {
                pending.pop();
                continue;
            }
            switch (fields.next()) {
                case TransferValue.Structured structure -> {
                    if (!snapshot.equals(structure.type().snapshot())) {
                        throw new SnapshotMismatchException(snapshot, structure.type().snapshot());
                    }
                    if (seen.add(structure)) {
                        pending.push(structure.values().values().iterator());
                    }
                }
                case TransferValue.Repeated repeated -> {
                    if (seen.add(repeated)) {
                        pending.push(repeated.values().iterator());
                    }
                }
                case TransferGeometry.Polyline line -> {
                    if (seen.add(line)) {
                        pending.push(line.lineAttributes().stream().iterator());
                    }
                }
                case TransferGeometry.MultiPolyline multi -> {
                    if (seen.add(multi)) {
                        pending.push(multi.lines().iterator());
                    }
                }
                case TransferGeometry.Surface surface -> {
                    if (seen.add(surface)) {
                        pending.push(surface.shell().lines().iterator());
                        pending.push(surface.holes().stream().flatMap(ring -> ring.lines().stream()).iterator());
                    }
                }
                case TransferGeometry.MultiSurface multi -> {
                    if (seen.add(multi)) {
                        pending.push(multi.surfaces().iterator());
                    }
                }
                default -> { }
            }
        }
    }
}
