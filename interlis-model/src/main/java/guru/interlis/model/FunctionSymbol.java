package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * FUNCTION declaration.
 *
 * @param header symbol header
 * @param parameters ordered parameters
 * @param returnType declared result type
 */
public record FunctionSymbol(SymbolHeader header, List<FunctionParameter> parameters,
                             String returnType) implements Symbol {
    /** Creates a validated instance. */
    public FunctionSymbol {
        Objects.requireNonNull(header, "header");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
        if (header.kind() != SymbolKind.FUNCTION) {
            throw new IllegalArgumentException("FunctionSymbol needs FUNCTION header");
        }
    }
}
