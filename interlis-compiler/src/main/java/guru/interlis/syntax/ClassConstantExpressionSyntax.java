package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Class constant such as {@code >ClassA}.
 *
 * @param id stable identifier
 * @param range source range
 * @param target referenced class
 */
public record ClassConstantExpressionSyntax(SyntaxNodeId id, SourceRange range, NameSyntax target)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public ClassConstantExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(target, "target");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_CLASS_CONSTANT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(target);
    }
}
