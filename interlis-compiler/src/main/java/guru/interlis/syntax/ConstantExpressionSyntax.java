package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Named constant such as PI or LNBASE.
 *
 * @param id stable identifier
 * @param range source range
 * @param constantName constant name
 */
public record ConstantExpressionSyntax(SyntaxNodeId id, SourceRange range, String constantName)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public ConstantExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(constantName, "constantName");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_CONSTANT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
