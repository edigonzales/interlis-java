package guru.interlis.transfer;

import guru.interlis.model.EffectiveMember;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.SymbolRef;

import java.util.Objects;

/**
 * An effective association role. References in the role declaration belong to the plan snapshot.
 *
 * @param symbol role declaration
 * @param name role names and original identity
 * @param member effective position and provenance
 * @param role bound association end, including targets, restrictions, cardinality and ordering
 */
public record RoleSlot(SymbolRef symbol, TransferName name, EffectiveMember member, RoleSymbol role)
        implements TransferSlot {
    public RoleSlot {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(role, "role");
    }
}
