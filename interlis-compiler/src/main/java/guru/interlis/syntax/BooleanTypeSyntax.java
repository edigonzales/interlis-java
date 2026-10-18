package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * BOOLEAN type.
 *
 * @param id stable identifier
 * @param range source range
 */
public record BooleanTypeSyntax(SyntaxNodeId id, SourceRange range) implements TypeSyntax {
    /** Creates a validated instance. */
    public BooleanTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_BOOLEAN;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
