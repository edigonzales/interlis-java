package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Effective attribute or role in deterministic order.
 *
 * @param declaredSymbol declared symbol
 * @param effectiveOwner effective owner
 * @param inheritancePath inheritance path
 * @param position effective position
 * @param origin origin information
 * @param overridden whether an inherited member is overridden
 */
public record EffectiveMember(SymbolId declaredSymbol, SymbolId effectiveOwner,
                              List<SymbolId> inheritancePath, int position,
                              EffectiveMemberOrigin origin, boolean overridden) {
    /** Creates a validated instance. */
    public EffectiveMember {
        Objects.requireNonNull(declaredSymbol, "declaredSymbol");
        Objects.requireNonNull(effectiveOwner, "effectiveOwner");
        inheritancePath = List.copyOf(Objects.requireNonNull(inheritancePath, "inheritancePath"));
        if (position < 0) {
            throw new IllegalArgumentException("position must be non-negative");
        }
        Objects.requireNonNull(origin, "origin");
    }
}
