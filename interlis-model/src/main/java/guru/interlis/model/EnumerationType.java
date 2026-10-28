package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Enumeration tree plus ORDERED/CIRCULAR/FINAL metadata.
 *
 * @param id stable identifier
 * @param origin origin information
 * @param values effective preorder, including inherited nodes and their original source ranges
 * @param ordered the {@code ordered} component
 * @param circular the {@code circular} component
 * @param baseType immediate effective base enum in the same snapshot, empty for a root
 * @param finalSubtrees closed sibling lists by parent path; empty string denotes the root
 * @param treeValues true for ALL OF: nodes and leaves are values; baseType
 *                   retains the referenced definition's effective identity
 */
public record EnumerationType(TypeId id, Origin origin, List<EnumerationValue> values,
                              boolean ordered, boolean circular, java.util.Set<String> finalSubtrees, java.util.Optional<TypeId> baseType,
                              boolean treeValues)
        implements TypeDescriptor {
    /** Creates a validated instance. */
    public EnumerationType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        Objects.requireNonNull(baseType, "baseType");
        finalSubtrees = java.util.Set.copyOf(Objects.requireNonNull(finalSubtrees, "finalSubtrees"));
    }

    /** Whether the root sibling list is closed; nested lists retain separate finality. */
    public boolean finalPart() { return finalSubtrees.contains(""); }

    @Override
    public TypeKind kind() {
        return TypeKind.ENUMERATION;
    }
}
