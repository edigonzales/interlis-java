package guru.interlis.syntax;

import guru.interlis.model.AlignmentKind;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * HALIGNMENT/VALIGNMENT type.
 *
 * @param id stable identifier
 * @param range source range
 * @param alignment the {@code alignment} component
 */
public record AlignmentTypeSyntax(SyntaxNodeId id, SourceRange range, AlignmentKind alignment)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public AlignmentTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(alignment, "alignment");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_ALIGNMENT;
    }

    @Override
    public java.util.List<SyntaxNode> children() {
        return java.util.List.of();
    }
}
