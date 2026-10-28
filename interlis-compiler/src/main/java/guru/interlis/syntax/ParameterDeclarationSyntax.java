package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Model-level runtime parameter; type syntax and name retain exact revision-bound ranges. */
public record ParameterDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
        Optional<SourceRange> nameRange, boolean mandatory, java.util.Set<guru.interlis.model.Modifier> modifiers, TypeSyntax type) implements DeclarationSyntax {
    public ParameterDeclarationSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name);
        Objects.requireNonNull(nameRange); modifiers = java.util.Set.copyOf(modifiers); Objects.requireNonNull(type);
    }
    @Override public SyntaxKind kind() { return SyntaxKind.PARAMETER; }
    @Override public List<SyntaxNode> children() { return List.of(type); }
}
