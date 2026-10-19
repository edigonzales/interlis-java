package guru.interlis.syntax;

import guru.interlis.model.NumericRange;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * COORD/MULTICOORD with per-axis ranges.
 *
 * @param id stable identifier
 * @param range source range
 * @param dimensions the {@code dimensions} component
 * @param axes the {@code axes} component
 * @param multiCoordinate the {@code multiCoordinate} component
 * @param referenceSystem optional reference system literal
 */
public record CoordinateTypeSyntax(SyntaxNodeId id, SourceRange range, int dimensions,
                                   List<NumericRange> axes, boolean multiCoordinate,
                                   java.util.Optional<String> referenceSystem)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public CoordinateTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        axes = List.copyOf(Objects.requireNonNull(axes, "axes"));
        if (dimensions < 1 || dimensions > 3 || axes.size() != dimensions) {
            throw new IllegalArgumentException("invalid coordinate syntax dimensions");
        }
        referenceSystem = Objects.requireNonNull(referenceSystem, "referenceSystem");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_COORDINATE;
    }

    @Override
    public List<SyntaxNode> children() {
        return new ArrayList<>();
    }
}
