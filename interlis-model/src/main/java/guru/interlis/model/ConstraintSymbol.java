package guru.interlis.model;

import java.util.Objects;

/**
 * Named or unnamed constraint with typed definition.
 *
 * @param header symbol header
 * @param definition constraint definition
 */
public record ConstraintSymbol(SymbolHeader header, ConstraintDefinition definition)
        implements Symbol {
    /** Creates a validated instance. */
    public ConstraintSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(definition, "definition");
        if (header.kind() != SymbolKind.CONSTRAINT) {
            throw new IllegalArgumentException("ConstraintSymbol needs CONSTRAINT header");
        }
    }
}
