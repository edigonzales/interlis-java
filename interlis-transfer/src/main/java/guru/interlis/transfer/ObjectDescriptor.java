package guru.interlis.transfer;

import guru.interlis.model.Cardinality;
import guru.interlis.model.Modifier;
import guru.interlis.model.OidRule;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.SymbolRef;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A class, structure or association in the checked closure. References belong to
 * the plan snapshot. Each role belongs to its semantic association.
 *
 * @param symbol object type declaration
 * @param name object type names and original identity
 * @param model declaring model
 * @param topic declaring topic, if present
 * @param kind class, structure or association kind
 * @param baseType immediate base declaration, if present
 * @param slots immutable attributes and roles in effective member order
 * @param oidRules immutable effective OID rules with their original provenance
 * @param modifiers immutable declaration modifiers
 * @param associationCardinality effective link multiplicity for an association, empty for other kinds
 */
public record ObjectDescriptor(SymbolRef symbol, TransferName name, SymbolRef model,
                               Optional<SymbolRef> topic, SymbolKind kind, Optional<SymbolRef> baseType,
                               List<TransferSlot> slots, List<OidRule> oidRules, Set<Modifier> modifiers,
                               Optional<Cardinality> associationCardinality) {
    public ObjectDescriptor {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(baseType, "baseType");
        slots = List.copyOf(slots);
        oidRules = List.copyOf(oidRules);
        modifiers = Set.copyOf(modifiers);
        Objects.requireNonNull(associationCardinality, "associationCardinality");
    }

    /** Whether the type can occur as a concrete object in a neutral basket stream. */
    public boolean transferable() {
        return topic.isPresent() && !modifiers.contains(Modifier.ABSTRACT)
                && (kind == SymbolKind.CLASS || kind == SymbolKind.ASSOCIATION);
    }
}
