package guru.interlis.model;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;

/** Value of a bound runtime parameter, class constant or attribute-path constant. The target kind distinguishes them.
 * Target and type belong to the same immutable graph; the range includes the source operator.
 * This node does not evaluate runtime values and has no child expressions.
 */
public record SymbolValueExpression(ExpressionId id, TypeId typeId, ExpressionTypeState typeState,
        SourceRange range, SymbolId target) implements TypedExpression {
    public SymbolValueExpression {
        Objects.requireNonNull(id); Objects.requireNonNull(typeId); Objects.requireNonNull(typeState);
        Objects.requireNonNull(range); Objects.requireNonNull(target);
    }
    @Override public List<ExpressionId> children() { return List.of(); }
}
