package guru.interlis.model;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;

/** ALL objects of the constraint bound by typeId, including specializations.
 * Immutable and null-free; the type and range belong to the containing snapshot.
 * An erroneous selection has ERROR state and an ErrorType, never an untyped ALL. */
public record ObjectSetExpression(ExpressionId id, TypeId typeId, ExpressionTypeState typeState,
        SourceRange range) implements TypedExpression {
    public ObjectSetExpression {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(typeState, "typeState"); Objects.requireNonNull(range, "range");
    }
    @Override public List<ExpressionId> children() { return List.of(); }
}
