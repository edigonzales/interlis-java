package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Binary operation with ordered operands.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param operator the {@code operator} component
 * @param left the {@code left} component
 * @param right the {@code right} component
 */
public record BinaryExpression(ExpressionId id, TypeId typeId,
                               ExpressionTypeState typeState, SourceRange range,
                               List<ExpressionId> children, BinaryOperator operator,
                               ExpressionId left, ExpressionId right)
        implements TypedExpression {
    /** Creates a validated instance. */
    public BinaryExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (!children.equals(List.of(left, right))) {
            throw new IllegalArgumentException("binary children must be [left, right]");
        }
    }
}
