package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/** Bound OID rule in the owning snapshot. Empty domain means explicit NO OID.
 * Inherited and topic-default rules retain their original provenance.
 * A missing/invalid binding is diagnosed and is never represented as NO OID. */
public record OidRule(boolean basket, Optional<SymbolId> domain, Origin origin) {
    public OidRule {
        Objects.requireNonNull(domain); Objects.requireNonNull(origin);
        if (basket && domain.isEmpty()) throw new IllegalArgumentException("basket rule requires a domain");
    }
}
