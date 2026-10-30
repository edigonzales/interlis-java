package guru.interlis.model;

import java.util.List;
import java.util.Objects;

/**
 * Effective attribute or role in deterministic order.
 *
 * @param declaredSymbol declared symbol
 * @param effectiveOwner effective owner
 * @param inheritancePath immutable, nonnull sequence from the selected
 *                        declaration's owner through every intermediate owner
 *                        to the effective owner, each once; local members have
 *                        the effective owner alone
 * @param position effective position
 * @param origin relation to the effective owner: inherited declarations use
 *               INHERITED even when their declaration overrides an ancestor
 * @param overridden whether the selected declaration overrides a base member
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
