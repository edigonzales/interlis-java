package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * CLASS/STRUCTURE value type.
 *
 * @param id stable identifier
 * @param range source range
 * @param structureValue whether this is a STRUCTURE value
 * @param restrictions restriction source names
 */
public record ClassValueTypeSyntax(SyntaxNodeId id, SourceRange range, boolean structureValue,
                                   List<NameSyntax> restrictions) implements TypeSyntax {
    /** Creates a validated instance. */
    public ClassValueTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        restrictions = List.copyOf(Objects.requireNonNull(restrictions, "restrictions"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_CLASS_VALUE;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.copyOf(restrictions);
    }
}
