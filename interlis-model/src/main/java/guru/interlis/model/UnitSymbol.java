package guru.interlis.model;

import java.util.Objects;
import java.util.Optional;

/**
 * UNIT declaration with optional base and definition text.
 *
 * @param header symbol header
 * @param abstractUnit whether the unit is abstract
 * @param base optional base unit
 * @param definition source definition text, if present
 */
public record UnitSymbol(SymbolHeader header, boolean abstractUnit,
                         Optional<SymbolId> base, Optional<String> definition)
        implements Symbol {
    /** Creates a validated instance. */
    public UnitSymbol {
        Objects.requireNonNull(header, "header");
        base = Objects.requireNonNull(base, "base");
        definition = Objects.requireNonNull(definition, "definition");
        if (header.kind() != SymbolKind.UNIT) {
            throw new IllegalArgumentException("UnitSymbol needs UNIT header");
        }
    }
}
