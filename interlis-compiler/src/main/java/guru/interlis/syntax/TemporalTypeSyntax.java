package guru.interlis.syntax;

import guru.interlis.model.TemporalKind;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * DATE, TIMEOFDAY or DATETIME type.
 *
 * @param id stable identifier
 * @param range source range
 * @param temporalKind the {@code temporalKind} component
 */
public record TemporalTypeSyntax(SyntaxNodeId id, SourceRange range, TemporalKind temporalKind)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public TemporalTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(temporalKind, "temporalKind");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_TEMPORAL;
    }

    @Override
    public java.util.List<SyntaxNode> children() {
        return java.util.List.of();
    }
}
