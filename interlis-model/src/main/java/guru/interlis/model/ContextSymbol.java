package guru.interlis.model;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;

/** Model-level 2.4 context. Mappings and alternatives retain declaration order and bound identities. */
public record ContextSymbol(SymbolHeader header, List<Mapping> mappings) implements Symbol {
    public ContextSymbol {
        Objects.requireNonNull(header); mappings = List.copyOf(mappings);
        if (header.kind() != SymbolKind.CONTEXT) throw new IllegalArgumentException("CONTEXT header required");
    }
    /** One mapping; range belongs to the generic-domain occurrence, all IDs to the same snapshot. */
    public record Mapping(SymbolId genericDomain, List<SymbolId> alternatives, SourceRange range) {
        public Mapping { Objects.requireNonNull(genericDomain); alternatives = List.copyOf(alternatives); Objects.requireNonNull(range); }
    }
}
