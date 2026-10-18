package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * View declaration; P1 keeps formation information explicit.
 *
 * @param header symbol header
 * @param bases the {@code bases} component
 * @param members member identifiers
 */
public record ViewSymbol(SymbolHeader header, List<SymbolId> bases,
                         List<SymbolId> members) implements Symbol {
    /** Creates a validated instance. */
    public ViewSymbol {
        Objects.requireNonNull(header, "header");
        bases = List.copyOf(Objects.requireNonNull(bases, "bases"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        if (header.kind() != SymbolKind.VIEW) {
            throw new IllegalArgumentException("ViewSymbol needs VIEW header");
        }
    }
}
