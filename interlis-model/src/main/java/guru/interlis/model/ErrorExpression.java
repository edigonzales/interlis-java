package guru.interlis.model;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Expression that could not be typed; carries at least one diagnostic cause.
 *
 * @param id stable identifier
 * @param typeId type identifier
 * @param typeState type state
 * @param range source range
 * @param children ordered child expressions
 * @param causes the {@code causes} component
 */
public record ErrorExpression(ExpressionId id, TypeId typeId,
                              ExpressionTypeState typeState, SourceRange range,
                              List<ExpressionId> children, List<guru.interlis.diagnostics.DiagnosticId> causes)
        implements TypedExpression {
    /** Creates a validated instance. */
    public ErrorExpression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        causes = List.copyOf(Objects.requireNonNull(causes, "causes"));
        if (causes.isEmpty()) {
            throw new IllegalArgumentException("ErrorExpression needs a cause");
        }
    }
}
