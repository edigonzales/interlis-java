package guru.interlis.syntax;
import guru.interlis.source.SourceRange;
import java.util.*;
/** METAOBJECT signature parameter; an absent OF target is resolved in the signature use context. */
public record MetaObjectTypeSyntax(SyntaxNodeId id, SourceRange range, Optional<NameSyntax> target) implements TypeSyntax {
    public MetaObjectTypeSyntax { Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(target); }
    @Override public SyntaxKind kind() { return SyntaxKind.METAOBJECT_TYPE; }
    @Override public List<SyntaxNode> children() { return target.<SyntaxNode>map(x->x).stream().toList(); }
}
