package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Visible unsupported declaration; it can never become a valid schema part.
 *
 * @param id stable identifier
 * @param range source range
 * @param featureId the {@code featureId} component
 * @param construct the {@code construct} component
 */
public record UnsupportedDeclarationSyntax(SyntaxNodeId id, SourceRange range,
                                           String featureId, String construct)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public UnsupportedDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(construct, "construct");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.UNSUPPORTED_DECLARATION;
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
