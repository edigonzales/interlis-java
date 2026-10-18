package guru.interlis.compiler.query;

import guru.interlis.model.Origin;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * Declaration target of a definition query.
 *
 * @param symbol the {@code symbol} component
 * @param declarationRange the {@code declarationRange} component
 * @param nameRange the {@code nameRange} component
 * @param origin origin information
 */
public record DefinitionTarget(SymbolRef symbol, SourceRange declarationRange,
                               SourceRange nameRange, Origin origin) {
    /** Creates a validated instance. */
    public DefinitionTarget {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(declarationRange, "declarationRange");
        Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(origin, "origin");
    }
}
