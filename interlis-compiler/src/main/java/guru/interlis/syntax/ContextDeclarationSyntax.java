package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Named 2.4 context with ordered, revision-bound domain mappings. */
public record ContextDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
        Optional<SourceRange> nameRange, List<Mapping> mappings) implements DeclarationSyntax {
    public ContextDeclarationSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name);
        Objects.requireNonNull(nameRange); mappings = List.copyOf(mappings);
    }
    public record Mapping(NameSyntax genericDomain, List<NameSyntax> alternatives) {
        public Mapping { Objects.requireNonNull(genericDomain); alternatives = List.copyOf(alternatives); }
    }
    @Override public SyntaxKind kind() { return SyntaxKind.CONTEXT; }
    @Override public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>();
        for (var mapping : mappings) { result.add(mapping.genericDomain()); result.addAll(mapping.alternatives()); }
        return List.copyOf(result);
    }
}
