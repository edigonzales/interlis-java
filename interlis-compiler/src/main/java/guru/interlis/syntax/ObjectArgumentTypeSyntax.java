package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Structured OBJECT/OBJECTS OF syntax with UTF-16 source ranges.
 * All components are non-null and immutable; an empty base means ANYCLASS.
 * Restrictions retain declaration order and their individual source ranges.
 */
public record ObjectArgumentTypeSyntax(SyntaxNodeId id, SourceRange range,
                                      Optional<NameSyntax> base, List<NameSyntax> restrictions, boolean objects)
        implements TypeSyntax {
    public ObjectArgumentTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(base, "base");
        restrictions = List.copyOf(restrictions);
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_OBJECT_ARGUMENT;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> children = new ArrayList<>();
        base.ifPresent(children::add);
        children.addAll(restrictions);
        return List.copyOf(children);
    }
}
