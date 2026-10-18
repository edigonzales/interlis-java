package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Parenthesized expression.
 *
 * @param id stable identifier
 * @param range source range
 * @param inner the {@code inner} component
 */
public record ParenthesizedExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                            ExpressionSyntax inner) implements ExpressionSyntax {
    /** Creates a validated instance. */
    public ParenthesizedExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(inner, "inner");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_PARENTHESIZED;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(inner);
    }
}
