package guru.interlis.syntax;

import guru.interlis.model.BlackboxKind;
import guru.interlis.source.SourceRange;

import java.util.Objects;

/**
 * BLACKBOX XML/BINARY type.
 *
 * @param id stable identifier
 * @param range source range
 * @param blackboxKind the {@code blackboxKind} component
 */
public record BlackboxTypeSyntax(SyntaxNodeId id, SourceRange range, BlackboxKind blackboxKind)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public BlackboxTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(blackboxKind, "blackboxKind");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_BLACKBOX;
    }

    @Override
    public java.util.List<SyntaxNode> children() {
        return java.util.List.of();
    }
}
