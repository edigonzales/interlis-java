package guru.interlis.syntax;

import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.NumericDirection;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Numeric range with optional open bounds. Source order and invalid bounds are
 * retained for semantic diagnostics; construction does not validate the language rule.
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
                                Optional<NameSyntax> unit, Optional<ReferenceSystem> referenceSystem)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public NumericTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(direction, "direction");
        unit = Objects.requireNonNull(unit, "unit");
        referenceSystem = Objects.requireNonNull(referenceSystem);

    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_NUMERIC;
    }

    @Override
    public List<SyntaxNode> children() {
        java.util.ArrayList<SyntaxNode> children = new java.util.ArrayList<>();
        unit.ifPresent(children::add); referenceSystem.ifPresent(r -> children.add(r.target())); return List.copyOf(children);
    }
    public record ReferenceSystem(NameSyntax target, boolean domain, java.util.OptionalInt axis) {
        public ReferenceSystem { Objects.requireNonNull(target); Objects.requireNonNull(axis); }
    }
}
