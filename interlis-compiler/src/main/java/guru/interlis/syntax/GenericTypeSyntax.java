package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/** Synthetic generic type used for INTERLIS ATTRIBUTE path values. */
public record GenericTypeSyntax(SyntaxNodeId id, SourceRange range, String name)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public GenericTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_GENERIC;
    }

    @Override
    public java.util.List<SyntaxNode> children() {
        return java.util.List.of();
    }
}
