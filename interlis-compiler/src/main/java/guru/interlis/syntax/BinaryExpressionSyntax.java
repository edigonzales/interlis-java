package guru.interlis.syntax;

import guru.interlis.model.BinaryOperator;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Binary operation syntax.
 *
 * @param id stable identifier
 * @param range source range
 * @param operator the {@code operator} component
 * @param left the {@code left} component
 * @param right the {@code right} component
 */
public record BinaryExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                     BinaryOperator operator, ExpressionSyntax left,
                                     ExpressionSyntax right) implements ExpressionSyntax {
    /** Creates a validated instance. */
    public BinaryExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_BINARY;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(left, right);
    }
}
