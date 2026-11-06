package guru.interlis.transfer;

import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TypeRef;

import java.util.Objects;

/**
 * An effective attribute in a plan object type. All references belong to the plan snapshot.
 *
 * @param symbol attribute declaration
 * @param name attribute names and original identity
 * @param member effective position and provenance
 * @param attribute bound declaration with source flags and override identity
 * @param effectiveType resolved type reference
 * @param cardinality effective value multiplicity
 */
public record AttributeSlot(SymbolRef symbol, TransferName name, EffectiveMember member,
                            AttributeSymbol attribute, TypeRef effectiveType, Cardinality cardinality)
        implements TransferSlot {
    public AttributeSlot {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(effectiveType, "effectiveType");
        Objects.requireNonNull(cardinality, "cardinality");
    }

    /** Returns the declaration's type, including any declared domain reference. */
    public TypeRef declaredType() {
        return new TypeRef(symbol.snapshot(), attribute.declaredType());
    }
}
