package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** ALL with an optional explicit object constraint; empty means the contextual class.
 * Values are immutable, null-free, and retain source/revision-bound UTF-16 ranges. */
public record ObjectSetExpressionSyntax(SyntaxNodeId id, SourceRange range,
        Optional<ObjectArgumentTypeSyntax> selection) implements ExpressionSyntax {
    public ObjectSetExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(selection, "selection");
    }
    @Override public SyntaxKind kind() { return SyntaxKind.EXPRESSION_OBJECT_SET; }
    @Override public List<SyntaxNode> children() { return selection.stream().<SyntaxNode>map(s -> s).toList(); }
}
