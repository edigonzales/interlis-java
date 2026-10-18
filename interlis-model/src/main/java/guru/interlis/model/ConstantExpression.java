package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Named language constant such as PI or LNBASE.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param constantName constant name
 */
public record ConstantExpression(ExpressionId id, TypeId typeId,
                                 ExpressionTypeState typeState, SourceRange range,
                                 List<ExpressionId> children, String constantName)
        implements TypedExpression {
    /** Creates a validated instance. */
    public ConstantExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(constantName, "constantName");
    }
}
