package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * DEFINED(path) with boolean result type.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param operand the {@code operand} component
 */
public record DefinedExpression(ExpressionId id, TypeId typeId,
                                ExpressionTypeState typeState, SourceRange range,
                                List<ExpressionId> children, ExpressionId operand)
        implements TypedExpression {
    /** Creates a validated instance. */
    public DefinedExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(operand, "operand");
    }
}
