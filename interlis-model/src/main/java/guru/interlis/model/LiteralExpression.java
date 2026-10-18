package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Text, numeric or boolean literal with exact number metadata.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param literalKind the {@code literalKind} component
 * @param lexeme the {@code lexeme} component
 * @param decimal the {@code decimal} component
 * @param textValue the {@code textValue} component
 */
public record LiteralExpression(ExpressionId id, TypeId typeId,
                                ExpressionTypeState typeState, SourceRange range,
                                List<ExpressionId> children, LiteralKind literalKind,
                                String lexeme, Optional<DecimalLiteral> decimal,
                                Optional<String> textValue) implements TypedExpression {
    /** Creates a validated instance. */
    public LiteralExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(literalKind, "literalKind");
        Objects.requireNonNull(lexeme, "lexeme");
        decimal = Objects.requireNonNull(decimal, "decimal");
        textValue = Objects.requireNonNull(textValue, "textValue");
    }
}
