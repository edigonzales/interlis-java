package guru.interlis.syntax;

import guru.interlis.model.Modifier;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Topic declaration.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param modifiers modifiers
 * @param baseTopic the {@code baseTopic} component
 * @param dependencies the {@code dependencies} component
 * @param members member identifiers
 * @param oidRules declared OID rules in source order
 * @param deferredGenerics explicitly declared 2.4 domain names, in source order
 */
public record TopicSyntax(SyntaxNodeId id, SourceRange range, String name,
                          Set<Modifier> modifiers, Optional<String> endName,
                          Optional<NameSyntax> baseTopic,
                          List<NameSyntax> dependencies, List<SyntaxNode> members,
                          List<OidRuleSyntax> oidRules, List<NameSyntax> deferredGenerics)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public TopicSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        endName = Objects.requireNonNull(endName, "endName");
        baseTopic = Objects.requireNonNull(baseTopic, "baseTopic");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        deferredGenerics = List.copyOf(deferredGenerics);
        oidRules = List.copyOf(Objects.requireNonNull(oidRules, "oidRules"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TOPIC;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>();
        baseTopic.ifPresent(result::add);
        oidRules.forEach(rule -> rule.domain().ifPresent(result::add));
        result.addAll(dependencies);
        result.addAll(deferredGenerics);
        result.addAll(members);
        return result;
    }
}
