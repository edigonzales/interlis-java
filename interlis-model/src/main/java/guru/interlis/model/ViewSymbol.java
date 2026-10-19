package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * View declaration; P1 keeps formation information explicit.
 *
 * @param header symbol header
 * @param bases the {@code bases} component
 * @param members member identifiers
 * @param formation preserved formation text
 * @param body preserved body text
 */
public record ViewSymbol(SymbolHeader header, List<SymbolId> bases,
                         List<SymbolId> members, Optional<String> formation,
                         Optional<String> body) implements Symbol {
    /** Creates a validated instance. */
    public ViewSymbol {
        Objects.requireNonNull(header, "header");
        bases = List.copyOf(Objects.requireNonNull(bases, "bases"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        formation = Objects.requireNonNull(formation, "formation");
        body = Objects.requireNonNull(body, "body");
        if (header.kind() != SymbolKind.VIEW) {
            throw new IllegalArgumentException("ViewSymbol needs VIEW header");
        }
    }
}
