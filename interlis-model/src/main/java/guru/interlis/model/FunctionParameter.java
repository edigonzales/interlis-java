package guru.interlis.model;

import java.util.Objects;

/**
 * Declared function parameter.
 *
 * @param name parameter name
 * @param typeName source-level type name
 */
public record FunctionParameter(String name, String typeName) {
    /** Creates a validated instance. */
    public FunctionParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeName, "typeName");
    }
}
