package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Path expression composed of name segments.
 *
 * @param id stable identifier
 * @param range source range
 * @param segments the {@code segments} component
 */
public record PathExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                   List<NameSyntax> segments) implements ExpressionSyntax {
    /** Creates a validated instance. */
    public PathExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("path expression needs segments");
        }
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_PATH;
    }

    @Override
    public List<SyntaxNode> children() {
        return new ArrayList<>(segments);
    }
}
