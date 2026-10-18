package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * Name reference with per-segment original ranges.
 *
 * @param id stable identifier
 * @param range source range
 * @param segments the {@code segments} component
 */
public record NameSyntax(SyntaxNodeId id, SourceRange range, List<NameSegmentSyntax> segments)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public NameSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("name syntax needs segments");
        }
    }

    /**
     * Executes {@code text}.
     *
     * @return the result
     */
    public String text() {
        return String.join(".", segments.stream().map(NameSegmentSyntax::text).toList());
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.NAME;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of();
    }

    /**
     * Immutable value.
     *
     * @param text the {@code text} component
     * @param range source range
     */
    public record NameSegmentSyntax(String text, SourceRange range) {
        /** Creates a validated instance. */
        public NameSegmentSyntax {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(range, "range");
        }
    }
}
