package guru.interlis.syntax;

import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.LiteralKind;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Literal syntax with exact number data where available.
 *
 * @param id stable identifier
 * @param range source range
 * @param literalKind the {@code literalKind} component
 * @param lexeme the {@code lexeme} component
 * @param decimal exact number value; empty for text
 * @param unit optional explicit unit, in its original source range
 */
public record LiteralExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                      LiteralKind literalKind, String lexeme,
                                      Optional<DecimalLiteral> decimal, Optional<NameSyntax> unit) implements ExpressionSyntax {
    /** Creates a validated instance. */
    public LiteralExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(literalKind, "literalKind");
        Objects.requireNonNull(lexeme, "lexeme");
        decimal = Objects.requireNonNull(decimal, "decimal");
        unit = Objects.requireNonNull(unit, "unit");
        if (literalKind != LiteralKind.NUMBER && unit.isPresent()) throw new IllegalArgumentException("only numbers have units");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_LITERAL;
    }

    @Override
    public List<SyntaxNode> children() {
        return unit.<List<SyntaxNode>>map(List::of).orElseGet(List::of);
    }
}
