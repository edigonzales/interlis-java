package guru.interlis.model;
import java.util.Objects;

/** Named external metadata object, owned by its basket, with a bound class. No transfer values are loaded.
 * An empty objectClass denotes failed binding in an erroneous analysis, never a valid schema. */
public record MetaObjectSymbol(SymbolHeader header, java.util.Optional<SymbolId> objectClass) implements Symbol {
    public MetaObjectSymbol { Objects.requireNonNull(header); Objects.requireNonNull(objectClass); }
}
