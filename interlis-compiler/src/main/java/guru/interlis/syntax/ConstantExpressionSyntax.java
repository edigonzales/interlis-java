package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Named constant such as PI or LNBASE.
 *
 * @param id stable identifier
 * @param range source range
 * @param constantName symbolic constant name; never a rounded decimal
 * @param unit optional explicit unit, in its original source range
 */
public record ConstantExpressionSyntax(SyntaxNodeId id, SourceRange range, String constantName, Optional<NameSyntax> unit)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public ConstantExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(constantName, "constantName");
        unit = Objects.requireNonNull(unit, "unit");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_CONSTANT;
    }

    @Override
    public List<SyntaxNode> children() {
        return unit.<List<SyntaxNode>>map(List::of).orElseGet(List::of);
    }
}
