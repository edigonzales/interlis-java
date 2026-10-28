package guru.interlis.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Topic, optionally a VIEW TOPIC.
 *
 * @param header symbol header
 * @param baseTopic the {@code baseTopic} component
 * @param members member identifiers
 * @param dependencies directly declared, bound topic IDs in source order; inherited declarations
 *                     are obtained through baseTopic, not copied into this immutable list
 * @param deferredGenerics directly declared domains in source order; inheritance follows baseTopic
 * @param genericBindings effective per-topic uses and alternatives, immutable and snapshot-bound
 */
public record TopicSymbol(SymbolHeader header, Optional<SymbolId> baseTopic,
                          List<SymbolId> members, List<SymbolId> dependencies,
                          List<SymbolId> deferredGenerics, List<GenericBinding> genericBindings) implements Symbol {
    /** Creates a validated instance. */
    public TopicSymbol {
        Objects.requireNonNull(header, "header");
        baseTopic = Objects.requireNonNull(baseTopic, "baseTopic");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        deferredGenerics = List.copyOf(deferredGenerics);
        genericBindings = List.copyOf(genericBindings);
        if (header.kind() != SymbolKind.TOPIC) {
            throw new IllegalArgumentException("TopicSymbol needs TOPIC header");
        }
    }
    /** Effective per-topic binding, including inherited uses, in first-use order.
     * Abstract bindings have no alternatives. Deferred bindings are legal transfer-time choices.
     * Invalid bindings are diagnosed and omitted, never represented as legal deferral.
     */
    public record GenericBinding(SymbolId genericDomain, List<SymbolId> alternatives,
            List<SymbolId> contexts, BindingState state) {
        public GenericBinding {
            Objects.requireNonNull(genericDomain); alternatives = List.copyOf(alternatives);
            contexts = List.copyOf(contexts); Objects.requireNonNull(state);
            if (state == BindingState.ABSTRACT && !alternatives.isEmpty()
                    || state == BindingState.CONCRETE && alternatives.size() != 1
                    || state == BindingState.DEFERRED && alternatives.isEmpty())
                throw new IllegalArgumentException("binding state and alternatives disagree");
        }
    }
    public enum BindingState { CONCRETE, DEFERRED, ABSTRACT }
}
