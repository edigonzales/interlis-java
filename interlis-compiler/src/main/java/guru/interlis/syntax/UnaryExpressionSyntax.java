package guru.interlis.syntax;

import guru.interlis.model.UnaryOperator;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Unary operation syntax.
 *
 * @param id stable identifier
 * @param range source range
 * @param operator the {@code operator} component
 * @param operand the {@code operand} component
 */
public record UnaryExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                    UnaryOperator operator, ExpressionSyntax operand)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public UnaryExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_UNARY;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(operand);
    }
}
