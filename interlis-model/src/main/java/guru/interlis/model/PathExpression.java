package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Typed object/attribute path expression.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param path name path
 */
public record PathExpression(ExpressionId id, TypeId typeId,
                             ExpressionTypeState typeState, SourceRange range,
                             List<ExpressionId> children, ObjectPath path)
        implements TypedExpression {
    /** Creates a validated instance. */
    public PathExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(path, "path");
    }
}
