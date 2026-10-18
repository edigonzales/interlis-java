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
 * @param dependencies the {@code dependencies} component
 */
public record TopicSymbol(SymbolHeader header, Optional<SymbolId> baseTopic,
                          List<SymbolId> members, List<SymbolId> dependencies) implements Symbol {
    /** Creates a validated instance. */
    public TopicSymbol {
        Objects.requireNonNull(header, "header");
        baseTopic = Objects.requireNonNull(baseTopic, "baseTopic");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        if (header.kind() != SymbolKind.TOPIC) {
            throw new IllegalArgumentException("TopicSymbol needs TOPIC header");
        }
    }
}
