package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Association with roles, own attributes and constraints.
 *
 * @param header symbol header
 * @param baseAssociation bound base, empty for a root association
 * @param cardinality effective link multiplicity for a participant combination
 * @param roles own role identifiers in declaration order; effective members include inherited roles
 * @param attributes attribute identifiers
 * @param constraints the {@code constraints} component
 * @param derivedFrom local basis identity; empty for a stored association
 */
public record AssociationSymbol(SymbolHeader header, Optional<SymbolId> baseAssociation, Cardinality cardinality, List<SymbolId> roles,
                                List<SymbolId> attributes, List<SymbolId> constraints, Optional<SymbolId> derivedFrom)
        implements Symbol {
    /** Creates a validated instance. */
    public AssociationSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(derivedFrom);
        Objects.requireNonNull(baseAssociation, "baseAssociation");
        Objects.requireNonNull(cardinality, "cardinality");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        if (header.kind() != SymbolKind.ASSOCIATION) {
            throw new IllegalArgumentException("AssociationSymbol needs ASSOCIATION header");
        }
    }
}
