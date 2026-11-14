package guru.interlis.validation;

import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferValue;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable values and trusted Java providers for a single schema snapshot. */
public record RuntimeBindings(Map<SymbolRef, TransferValue> parameters,
                              Map<SymbolRef, FunctionBinding> functions, Optional<GeometryEngine> geometry) {
    public RuntimeBindings {
        parameters = Map.copyOf(parameters);
        functions = Map.copyOf(functions);
        Objects.requireNonNull(geometry, "geometry");
    }

    public RuntimeBindings(Map<SymbolRef, TransferValue> parameters, Map<SymbolRef, FunctionBinding> functions) {
        this(parameters, functions, Optional.empty());
    }

    /** Returns a new binding with the explicitly selected geometry engine. */
    public RuntimeBindings withGeometry(GeometryEngine engine) {
        return new RuntimeBindings(parameters, functions, Optional.of(engine));
    }

    public static RuntimeBindings empty() {
        return new RuntimeBindings(Map.of(), Map.of());
    }
}
