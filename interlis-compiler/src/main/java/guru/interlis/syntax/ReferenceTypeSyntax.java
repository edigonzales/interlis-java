package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * REFERENCE TO target.
 *
 * @param id stable identifier
 * @param range source range
 * @param target target symbol
 * @param restrictions ordered restriction names, immutable and null-free
 * @param external external-role marker
 */
public record ReferenceTypeSyntax(SyntaxNodeId id, SourceRange range, NameSyntax target,
                                  List<NameSyntax> restrictions, boolean external) implements TypeSyntax {
    /** Creates a validated instance. */
    public ReferenceTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(target, "target");
        restrictions = List.copyOf(restrictions);
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_REFERENCE;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> children = new java.util.ArrayList<>();
        children.add(target); children.addAll(restrictions);
        return List.copyOf(children);
    }
}
