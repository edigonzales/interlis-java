package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.Objects;

/** {@code ALL OF Domain} enumeration-tree value type. */
public record EnumTreeValueSyntax(SyntaxNodeId id, SourceRange range, NameSyntax target)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public EnumTreeValueSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_ENUM_TREE;
    }

    @Override
    public java.util.List<SyntaxNode> children() {
        return java.util.List.of(target);
    }
}
