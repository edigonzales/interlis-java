package guru.interlis.xtf23;

import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.TransferId;

import java.util.HashSet;
import java.util.Set;

/** One FULL transfer's shared BID/TID space. The wire spelling is independent of IdOrigin. */
final class Xtf23IdentityScope {
    private Set<String> ids = new HashSet<>();

    boolean claim(BasketId id) {
        return ids.add(id.value());
    }

    boolean claim(TransferId id) {
        return ids.add(id.value());
    }

    void release() {
        ids = Set.of();
    }
}
