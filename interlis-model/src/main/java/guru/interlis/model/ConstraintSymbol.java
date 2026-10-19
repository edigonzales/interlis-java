package guru.interlis.model;

import java.util.Objects;

/**
 * Named or unnamed constraint with typed definition.
 *
 * @param header symbol header
 * @param definition constraint definition
 * @param target optional external target class/association
 */
public record ConstraintSymbol(SymbolHeader header, ConstraintDefinition definition,
                               java.util.Optional<SymbolId> target)
        implements Symbol {
    /** Creates a validated instance. */
    public ConstraintSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(definition, "definition");
        target = Objects.requireNonNull(target, "target");
        if (header.kind() != SymbolKind.CONSTRAINT) {
            throw new IllegalArgumentException("ConstraintSymbol needs CONSTRAINT header");
        }
    }
}
