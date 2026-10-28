package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * FUNCTION declaration.
 *
 * @param header symbol header
 * @param parameters ordered parameters
 * @param returnType declared result type
 * @param returnMandatory whether the declared result excludes undefined
 * @param explanation external function explanation, with its original source range
 */
public record FunctionSymbol(SymbolHeader header, List<FunctionParameter> parameters,
                             TypeId returnType, boolean returnMandatory,Documentation explanation) implements Symbol {
    /** Creates a validated instance. */
    public FunctionSymbol {
        Objects.requireNonNull(header, "header");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        Objects.requireNonNull(returnType, "returnType");
        Objects.requireNonNull(explanation,"explanation");
        if (header.kind() != SymbolKind.FUNCTION) {
            throw new IllegalArgumentException("FunctionSymbol needs FUNCTION header");
        }
    }
}
