package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Original source region skipped by recovery.
 *
 * @param id stable identifier
 * @param range source range
 * @param reason the {@code reason} component
 */
public record SkippedSyntax(SyntaxNodeId id, SourceRange range, String reason)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public SkippedSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.SKIPPED;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }
}
