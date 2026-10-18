package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Function call; P1 may mark the call unsupported rather than guess a signature.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param functionName the {@code functionName} component
 * @param targetFunction the {@code targetFunction} component
 */
public record FunctionCallExpression(ExpressionId id, TypeId typeId,
                                     ExpressionTypeState typeState, SourceRange range,
                                     List<ExpressionId> children, String functionName,
                                     Optional<SymbolId> targetFunction)
        implements TypedExpression {
    /** Creates a validated instance. */
    public FunctionCallExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(functionName, "functionName");
        targetFunction = Objects.requireNonNull(targetFunction, "targetFunction");
    }
}
