package guru.interlis.compiler.query;

import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * Typed hover payload; hosts render escaped markdown/plain text.
 *
 * @param symbol the {@code symbol} component
 * @param signature the {@code signature} component
 * @param documentation the {@code documentation} component
 * @param range source range
 */
public record HoverInfo(SymbolRef symbol, String signature, String documentation,
                        SourceRange range) {
    /** Creates a validated instance. */
    public HoverInfo {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(documentation, "documentation");
        Objects.requireNonNull(range, "range");
    }
}
