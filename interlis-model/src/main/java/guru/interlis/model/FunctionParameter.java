package guru.interlis.model;

import java.util.Objects;

/**
 * Declared function parameter.
 *
 * @param name parameter name
 * @param typeId declared type in the containing immutable semantic graph
 * @param mandatory whether an undefined argument is excluded
 */
public record FunctionParameter(String name, TypeId typeId, boolean mandatory) {
    /** Creates a validated instance. */
    public FunctionParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(typeId, "typeId");
    }
}
