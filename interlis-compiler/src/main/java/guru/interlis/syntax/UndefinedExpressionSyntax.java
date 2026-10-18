package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * UNDEFINED language constant.
 *
 * @param id stable identifier
 * @param range source range
 */
public record UndefinedExpressionSyntax(SyntaxNodeId id, SourceRange range)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public UndefinedExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_UNDEFINED;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
