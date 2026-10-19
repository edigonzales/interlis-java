package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/** Synthetic constant used for INTERLIS attribute-path constants. */
public record GenericConstantExpressionSyntax(SyntaxNodeId id, SourceRange range, String name)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public GenericConstantExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_GENERIC_CONSTANT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
