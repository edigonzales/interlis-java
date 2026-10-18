package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Association with roles, own attributes and constraints.
 *
 * @param header symbol header
 * @param roles role identifiers
 * @param attributes attribute identifiers
 * @param constraints the {@code constraints} component
 */
public record AssociationSymbol(SymbolHeader header, List<SymbolId> roles,
                                List<SymbolId> attributes, List<SymbolId> constraints)
        implements Symbol {
    /** Creates a validated instance. */
    public AssociationSymbol {
        Objects.requireNonNull(header, "header");
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        if (header.kind() != SymbolKind.ASSOCIATION) {
            throw new IllegalArgumentException("AssociationSymbol needs ASSOCIATION header");
        }
    }
}
