package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Class declaration.
 *
 * @param header symbol header
 * @param baseType optional base class or structure identifier
 * @param members member identifiers
 * @param constraints the {@code constraints} component
 */
public record ClassSymbol(SymbolHeader header, Optional<SymbolId> baseType,
                          List<SymbolId> members, List<SymbolId> constraints) implements Symbol {
    /** Creates a validated instance. */
    public ClassSymbol {
        Objects.requireNonNull(header, "header");
        baseType = Objects.requireNonNull(baseType, "baseType");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        if (header.kind() != SymbolKind.CLASS) {
            throw new IllegalArgumentException("ClassSymbol needs CLASS header");
        }
    }
}
