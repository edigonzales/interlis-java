package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Unary operation.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param operator the {@code operator} component
 * @param operand the {@code operand} component
 */
public record UnaryExpression(ExpressionId id, TypeId typeId,
                              ExpressionTypeState typeState, SourceRange range,
                              List<ExpressionId> children, UnaryOperator operator,
                              ExpressionId operand) implements TypedExpression {
    /** Creates a validated instance. */
    public UnaryExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(operand, "operand");
        if (!children.equals(List.of(operand))) {
            throw new IllegalArgumentException("unary children must be [operand]");
        }
    }
}
