package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Recovered region retaining known child nodes and its cause.
 *
 * @param id stable identifier
 * @param range source range
 * @param children ordered child expressions
 * @param reason the {@code reason} component
 */
public record RecoveredSyntax(SyntaxNodeId id, SourceRange range, List<SyntaxNode> children,
                              String reason) implements SyntaxNode {
    /** Creates a validated instance. */
    public RecoveredSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        children = List.copyOf(Objects.requireNonNull(children, "children"));
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.RECOVERED;
    }

    @Override
    public boolean synthetic() {
        return true;
    }
}
