package guru.interlis.model;

import guru.interlis.source.SourceRange;
import java.util.*;

/** A BAG of inspected structure elements. The optional view retains named-view
 * selections/derivations; source identifies the class/structure being inspected.
 * path identifies its decomposition, restriction optionally selects one source object.
 * IDs and the UTF-16 range belong to this snapshot. All values are immutable and
 * null-free. Invalid targets are represented by ErrorExpression instead. */
public record InspectionExpression(ExpressionId id, TypeId typeId, ExpressionTypeState typeState,
        SourceRange range, Optional<SymbolId> view, SymbolId source, SymbolId element,
        ExpressionId path, Optional<ExpressionId> restriction, boolean area) implements TypedExpression {
    public InspectionExpression {
        Objects.requireNonNull(id); Objects.requireNonNull(typeId); Objects.requireNonNull(typeState);
        Objects.requireNonNull(range); Objects.requireNonNull(view); Objects.requireNonNull(source);
        Objects.requireNonNull(element); Objects.requireNonNull(path); Objects.requireNonNull(restriction);
    }
    @Override public List<ExpressionId> children() {
        return restriction.map(r -> List.of(path, r)).orElseGet(() -> List.of(path));
    }
}
