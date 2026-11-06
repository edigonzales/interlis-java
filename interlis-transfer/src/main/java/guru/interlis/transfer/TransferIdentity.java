package guru.interlis.transfer;

import guru.interlis.source.SourceId;

import java.util.Objects;
import java.util.Optional;

/** Identity and provenance of one object occurrence.
 * The object's descriptor carries the applicable OID domain and inheritance rules.
 *
 * @param currentBasket basket containing this occurrence
 * @param originalBasket original basket when supplied independently by the source
 * @param tid object identifier scoped to the transfer, if present
 * @param oid independently supplied stable object identifier, if present
 * @param source opaque data source identifier, if known
 */
public record TransferIdentity(BasketId currentBasket, Optional<BasketId> originalBasket,
                               Optional<TransferId> tid, Optional<ObjectOid> oid, Optional<SourceId> source) {
    public TransferIdentity {
        Objects.requireNonNull(currentBasket, "currentBasket");
        Objects.requireNonNull(originalBasket, "originalBasket");
        Objects.requireNonNull(tid, "tid");
        Objects.requireNonNull(oid, "oid");
        Objects.requireNonNull(source, "source");
    }

    /** Provenance of a transfer-scoped identifier. */
    public enum IdOrigin {
        /** Supplied by the transfer source. */
        SOURCE,
        /** Assigned for this transfer occurrence. */
        SYNTHETIC
    }

    /** Identifier of a basket in a transfer. */
    public record BasketId(String value, IdOrigin origin) {
        public BasketId {
            requireIdentifier(value);
            Objects.requireNonNull(origin, "origin");
        }
    }

    /** Identity carried by a reference. Its variant determines the scope. */
    public sealed interface ObjectKey permits TransferId, ObjectOid {
        /** Returns the exact opaque identifier as supplied. */
        String value();
    }

    /** Transfer-scoped object identity with explicit source or synthetic provenance. */
    public record TransferId(String value, IdOrigin origin) implements ObjectKey {
        public TransferId {
            requireIdentifier(value);
            Objects.requireNonNull(origin, "origin");
        }
    }

    /** Stable object identity supplied separately from any transfer-scoped identifier. */
    public record ObjectOid(String value) implements ObjectKey {
        public ObjectOid {
            requireIdentifier(value);
        }
    }

    private static void requireIdentifier(String value) {
        if (Objects.requireNonNull(value, "value").isEmpty()) {
            throw new IllegalArgumentException("An identifier must have a value");
        }
    }
}
