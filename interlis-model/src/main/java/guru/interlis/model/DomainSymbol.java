package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Named domain (alias or restricting extension).
 *
 * @param header symbol header
 * @param typeId type identifier
 * @param baseDomain the {@code baseDomain} component
 * @param generic the {@code generic} component
 */
public record DomainSymbol(SymbolHeader header, TypeId typeId,
                           Optional<SymbolId> baseDomain, boolean generic) implements Symbol {
    /** Creates a validated instance. */
    public DomainSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(typeId, "typeId");
        baseDomain = Objects.requireNonNull(baseDomain, "baseDomain");
        if (header.kind() != SymbolKind.DOMAIN) {
            throw new IllegalArgumentException("DomainSymbol needs DOMAIN header");
        }
    }
    /** Effective required-value flag, inherited from a mandatory base domain. */
    public boolean mandatory() { return header.has(Modifier.MANDATORY); }
}
