package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Structure declaration.
 *
 * @param header symbol header
 * @param baseType optional base type
 * @param members member identifiers
 * @param constraints the {@code constraints} component
 */
public record StructureSymbol(SymbolHeader header, Optional<SymbolId> baseType,
                              List<SymbolId> members, List<SymbolId> constraints) implements Symbol {
    /** Creates a validated instance. */
    public StructureSymbol {
        Objects.requireNonNull(header, "header");
        baseType = Objects.requireNonNull(baseType, "baseType");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        if (header.kind() != SymbolKind.STRUCTURE) {
            throw new IllegalArgumentException("StructureSymbol needs STRUCTURE header");
        }
    }
}
