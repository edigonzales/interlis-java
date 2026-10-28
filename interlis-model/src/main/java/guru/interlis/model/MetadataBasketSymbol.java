package guru.interlis.model;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Bound metadata basket declaration. Objects are locally declared, ordered identities; baseBasket supplies inherited objects.
 * A missing topic is permitted only in an erroneous analysis; references belong to the same graph. */
public record MetadataBasketSymbol(SymbolHeader header, Kind basketKind, Optional<SymbolId> topic,
        Optional<SymbolId> baseBasket, List<SymbolId> objects) implements Symbol {
    public MetadataBasketSymbol {
        Objects.requireNonNull(header); Objects.requireNonNull(basketKind); Objects.requireNonNull(topic);
        Objects.requireNonNull(baseBasket); objects = List.copyOf(objects);
    }
    public enum Kind { REFSYSTEM, SIGN }
}
