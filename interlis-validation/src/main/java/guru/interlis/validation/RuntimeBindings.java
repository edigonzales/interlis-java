package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferValue;

import java.util.Map;

/** Immutable values and trusted Java providers for a single schema snapshot. */
public record RuntimeBindings(Map<SymbolRef, TransferValue> parameters,
                              Map<SymbolRef, FunctionBinding> functions) {
    public RuntimeBindings {
        parameters = Map.copyOf(parameters);
        functions = Map.copyOf(functions);
    }

    public static RuntimeBindings empty() {
        return new RuntimeBindings(Map.of(), Map.of());
    }
}
