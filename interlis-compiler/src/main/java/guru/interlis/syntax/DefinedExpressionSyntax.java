package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * DEFINED(path) syntax.
 *
 * @param id stable identifier
 * @param range source range
 * @param operand the {@code operand} component
 */
public record DefinedExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                      ExpressionSyntax operand) implements ExpressionSyntax {
    /** Creates a validated instance. */
    public DefinedExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(operand, "operand");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_DEFINED;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(operand);
    }
}
