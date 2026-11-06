package guru.interlis.transfer;

import guru.interlis.model.OidRule;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TopicSymbol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Topic and basket rules. References, including IDs within model descriptors,
 * belong to the plan snapshot.
 *
 * @param symbol topic declaration
 * @param name topic names and original identity
 * @param model declaring model
 * @param baseTopic immediate base topic, if present
 * @param dependencies immutable list of directly declared topic dependencies in source order
 * @param objectTypes immutable list of object type references in plan order
 * @param oidRules immutable effective OID rules, with the basket rule before the object default
 * @param genericBindings immutable effective generic bindings in first-use order
 * @param abstractTopic whether the topic is abstract
 */
public record TopicDescriptor(SymbolRef symbol, TransferName name, SymbolRef model,
                              Optional<SymbolRef> baseTopic, List<SymbolRef> dependencies,
                              List<SymbolRef> objectTypes,
                              List<OidRule> oidRules, List<TopicSymbol.GenericBinding> genericBindings,
                              boolean abstractTopic) {
    public TopicDescriptor {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(baseTopic, "baseTopic");
        dependencies = List.copyOf(dependencies);
        objectTypes = List.copyOf(objectTypes);
        oidRules = List.copyOf(oidRules);
        genericBindings = List.copyOf(genericBindings);
    }
}
