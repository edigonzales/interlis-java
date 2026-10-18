package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * UNDEFINED language constant; not an error type and not Java null.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 */
public record UndefinedExpression(ExpressionId id, TypeId typeId,
                                  ExpressionTypeState typeState, SourceRange range,
                                  List<ExpressionId> children) implements TypedExpression {
    /** Creates a validated instance. */
    public UndefinedExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }
}
