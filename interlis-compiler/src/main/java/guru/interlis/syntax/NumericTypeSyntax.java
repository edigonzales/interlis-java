package guru.interlis.syntax;

import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.NumericDirection;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Numeric range with optional open bounds.
 *
 * @param id stable identifier
 * @param range source range
 * @param minimum the {@code minimum} component
 * @param maximum the {@code maximum} component
 * @param circular the {@code circular} component
 * @param direction the {@code direction} component
 * @param unit optional unit reference
 */
public record NumericTypeSyntax(SyntaxNodeId id, SourceRange range,
                                Optional<DecimalLiteral> minimum,
                                Optional<DecimalLiteral> maximum,
                                boolean circular, NumericDirection direction,
                                Optional<NameSyntax> unit)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public NumericTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(direction, "direction");
        unit = Objects.requireNonNull(unit, "unit");
        if (minimum.isPresent() && maximum.isPresent()
                && minimum.get().exactValue().compareTo(maximum.get().exactValue()) > 0) {
            throw new IllegalArgumentException("numeric syntax minimum exceeds maximum");
        }
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_NUMERIC;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
