package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Enumeration-style constant such as {@code #true}.
 *
 * @param id stable identifier
 * @param range source range
 * @param text source text including the leading hash
 */
public record EnumerationConstantExpressionSyntax(SyntaxNodeId id, SourceRange range, String text)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public EnumerationConstantExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(text, "text");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_ENUM_CONSTANT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
