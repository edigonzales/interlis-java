package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Path expression composed of identifier segments with preserved association-access operators.
 *
 * @param id stable identifier
 * @param range source range
 * @param segments the {@code segments} component
 */
public record PathExpressionSyntax(SyntaxNodeId id, SourceRange range,
                                   List<Segment> segments) implements ExpressionSyntax {
    /** Creates a validated instance. */
    public PathExpressionSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("path expression needs segments");
        }
    }

    /**
     * One immutable, null-free path step. The range includes operators and brackets;
     * name and optional association qualifier have their own revision-bound UTF-16 ranges.
     * A qualifier selects an outgoing source role; associationAccess denotes backslash access.
     */
    public record Segment(NameSyntax name, boolean associationAccess, Optional<NameSyntax> associationQualifier,
                          Optional<guru.interlis.model.PathIndex> index, SourceRange range) {
        public Segment { Objects.requireNonNull(name, "name"); Objects.requireNonNull(range, "range");
            Objects.requireNonNull(associationQualifier, "associationQualifier");
            Objects.requireNonNull(index, "index");
            if (index.isPresent() && (associationAccess || associationQualifier.isPresent()))
                throw new IllegalArgumentException("attribute index cannot qualify an association");
        }
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXPRESSION_PATH;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> children = new ArrayList<>();
        for (Segment segment : segments) {
            children.add(segment.name());
            segment.associationQualifier().ifPresent(children::add);
        }
        return List.copyOf(children);
    }
}
