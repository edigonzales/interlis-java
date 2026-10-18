package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Constraint declaration with an optional typed expression.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param constraintKind the {@code constraintKind} component
 * @param expression the {@code expression} component
 */
public record ConstraintDeclarationSyntax(SyntaxNodeId id, SourceRange range,
                                          Optional<String> name, ConstraintKind constraintKind,
                                          Optional<ExpressionSyntax> expression)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public ConstraintDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(constraintKind, "constraintKind");
        expression = Objects.requireNonNull(expression, "expression");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.CONSTRAINT;
    }

    @Override
    public List<SyntaxNode> children() {
        return expression.<List<SyntaxNode>>map(List::of).orElseGet(List::of);
    }
}
