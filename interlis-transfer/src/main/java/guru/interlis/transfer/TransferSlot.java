package guru.interlis.transfer;

import guru.interlis.model.EffectiveMember;
import guru.interlis.model.SymbolRef;

/** One immutable member of an object type, bound to the same snapshot as its owner. */
public sealed interface TransferSlot permits AttributeSlot, RoleSlot {
    /** Returns the effective member's declaration. */
    SymbolRef symbol();

    /** Returns the declaration's names and original identity. */
    TransferName name();

    /** Returns the effective position, inheritance path and override provenance. */
    EffectiveMember member();
}
