package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Explicitly unsupported expression construct.
 *
 * @param id stable identifier
 * @param range source range
 * @param featureId the {@code featureId} component
 * @param construct the {@code construct} component
 */
public record UnsupportedExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                          String featureId, String construct)
        implements ExpressionSyntax {
    /** Creates a validated instance. */
    public UnsupportedExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(construct, "construct");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_UNSUPPORTED;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }

    @Override
    public boolean synthetic() {
        return true;
    }
}
