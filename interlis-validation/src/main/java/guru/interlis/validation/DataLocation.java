package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.ObjectOid;
import guru.interlis.transfer.TransferIdentity.TransferId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Data provenance and immutable path in outer-to-inner order, with no inferred wire position. */
public record DataLocation(Optional<SourceId> source, BasketId basket, Optional<TransferId> tid,
                           Optional<ObjectOid> oid, SymbolRef objectType, List<Step> path) {
    public DataLocation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(basket, "basket");
        Objects.requireNonNull(tid, "tid");
        Objects.requireNonNull(oid, "oid");
        Objects.requireNonNull(objectType, "objectType");
        path = List.copyOf(path);
    }

    /** One segment in the data path. */
    public sealed interface Step permits Slot, Index { }

    /** Snapshot-bound attribute or role. */
    public record Slot(SymbolRef symbol) implements Step {
        public Slot {
            Objects.requireNonNull(symbol, "symbol");
        }
    }

    /** One-based collection position. */
    public record Index(int position) implements Step {
        public Index {
            if (position < 1) {
                throw new IllegalArgumentException("Position must be positive");
            }
        }
    }
}
