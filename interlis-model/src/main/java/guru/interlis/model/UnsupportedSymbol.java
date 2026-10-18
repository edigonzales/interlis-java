package guru.interlis.model;

import java.util.Objects;

/**
 * Visible unsupported declaration; never a silently valid generic element.
 *
 * @param header symbol header
 * @param featureId the {@code featureId} component
 * @param construct the {@code construct} component
 */
public record UnsupportedSymbol(SymbolHeader header, String featureId, String construct)
        implements Symbol {
    /** Creates a validated instance. */
    public UnsupportedSymbol {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(construct, "construct");
        if (header.kind() != SymbolKind.UNSUPPORTED) {
            throw new IllegalArgumentException("UnsupportedSymbol needs UNSUPPORTED header");
        }
    }
}
