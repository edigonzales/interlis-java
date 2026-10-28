package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;

/** PARAMETER selects the separate runtime-parameter namespace. */
public record ParameterExpressionSyntax(SyntaxNodeId id, SourceRange range, NameSyntax parameter) implements ExpressionSyntax {
    public ParameterExpressionSyntax { Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(parameter); }
    @Override public SyntaxKind kind() { return SyntaxKind.EXPRESSION_PARAMETER; }
    @Override public List<SyntaxNode> children() { return List.of(parameter); }
}
