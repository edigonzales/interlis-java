package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.ObjectOid;
import guru.interlis.transfer.TransferIdentity.TransferId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Data provenance and immutable outer-to-inner path. Dataset findings can omit
 * a basket. Basket findings can omit the object type. No wire position is inferred.
 */
public record DataLocation(Optional<SourceId> source, Optional<BasketId> basket, Optional<TransferId> tid,
                           Optional<ObjectOid> oid, Optional<SymbolRef> objectType, List<Step> path) {
    public DataLocation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(basket, "basket");
        Objects.requireNonNull(tid, "tid");
        Objects.requireNonNull(oid, "oid");
        Objects.requireNonNull(objectType, "objectType");
        path = List.copyOf(path);
        if (basket.isEmpty() && (tid.isPresent() || oid.isPresent())) {
            throw new IllegalArgumentException("Object identity needs a basket");
        }
    }

    /** Creates a location within a delivered basket. */
    public DataLocation(Optional<SourceId> source, BasketId basket, Optional<TransferId> tid,
                        Optional<ObjectOid> oid, Optional<SymbolRef> objectType, List<Step> path) {
        this(source, Optional.of(basket), tid, oid, objectType, path);
    }

    /** One segment in the data path. */
    public sealed interface Step permits Slot, Index, GeometryComponent { }

    /** Snapshot-bound attribute or role. */
    public record Slot(SymbolRef symbol) implements Step {
        public Slot {
            Objects.requireNonNull(symbol, "symbol");
        }
    }

    /** Named geometry component with a one-based position within its parent. */
    public record GeometryComponent(Kind kind, int position) implements Step {
        public GeometryComponent {
            Objects.requireNonNull(kind, "kind");
            if (position < 1) {
                throw new IllegalArgumentException("Position must be positive");
            }
        }

        /** Component in the immutable geometry tree. */
        public enum Kind { COORDINATE, SURFACE, RING, LINE, PART, START, SEGMENT, SUPPORT, END, AXIS }
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
