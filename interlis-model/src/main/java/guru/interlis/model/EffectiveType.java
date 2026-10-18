package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Declared type plus alias/restriction path to the effective type.
 *
 * @param declaredType the {@code declaredType} component
 * @param effectiveType the {@code effectiveType} component
 * @param aliasPath the {@code aliasPath} component
 */
public record EffectiveType(TypeId declaredType, TypeId effectiveType,
                            List<SymbolId> aliasPath) {
    /** Creates a validated instance. */
    public EffectiveType {
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(effectiveType, "effectiveType");
        aliasPath = List.copyOf(Objects.requireNonNull(aliasPath, "aliasPath"));
    }
}
