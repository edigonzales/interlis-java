package guru.interlis.model;
import java.util.*;
/** Bound graphic definition. Null-free, immutable, graph-bound; local rules and effective rules retain declaration order.
 * Selections include inherited selections in base-first order. Missing basis is only valid in erroneous analysis. */
public record GraphicSymbol(SymbolHeader header, Optional<SymbolId> baseGraphic, Optional<SymbolId> basis,
        List<ExpressionId> selections, List<SymbolId> rules, List<SymbolId> effectiveRules) implements Symbol {
    public GraphicSymbol { Objects.requireNonNull(header); Objects.requireNonNull(baseGraphic); Objects.requireNonNull(basis);
        selections=List.copyOf(selections); rules=List.copyOf(rules); effectiveRules=List.copyOf(effectiveRules); }
}
