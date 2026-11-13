package guru.interlis.validation;

import guru.interlis.model.FunctionSymbol;

import java.util.Objects;

/** Connects a bound declaration to one version of a trusted implementation. */
public record FunctionBinding(FunctionSymbol signature, String implementationId, String version,
                              ValidationFunction implementation) {
    public FunctionBinding {
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(implementation, "implementation");
        if (implementationId.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("A function implementation needs an ID and version");
        }
    }
}
