package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Attribute-path constant (>>), with an optional explicitly qualified viewable. */
public record AttributeConstantExpressionSyntax(SyntaxNodeId id, SourceRange range,
        Optional<NameSyntax> viewable, NameSyntax attribute) implements ExpressionSyntax {
    public AttributeConstantExpressionSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(viewable); Objects.requireNonNull(attribute);
    }
    @Override public SyntaxKind kind() { return SyntaxKind.EXPRESSION_ATTRIBUTE_CONSTANT; }
    @Override public List<SyntaxNode> children() { return viewable.isPresent() ? List.of(viewable.orElseThrow(), attribute) : List.of(attribute); }
}
