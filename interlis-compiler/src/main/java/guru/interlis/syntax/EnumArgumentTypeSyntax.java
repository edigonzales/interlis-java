package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;

/** Source ENUMVAL/ENUMTREEVAL in a function signature, with its original range. */
public record EnumArgumentTypeSyntax(SyntaxNodeId id, SourceRange range, boolean treeValues) implements TypeSyntax {
    public EnumArgumentTypeSyntax { Objects.requireNonNull(id); Objects.requireNonNull(range); }
    @Override public SyntaxKind kind() { return SyntaxKind.TYPE_ENUM_ARGUMENT; }
    @Override public List<SyntaxNode> children() { return List.of(); }
}
