package guru.interlis.syntax;

import guru.interlis.model.GeometryKind;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * LINE TYPE / geometry type syntax.
 *
 * @param id stable identifier
 * @param range source range
 * @param geometryKind geometry family
 * @param lineForm declared line form text
 * @param vertexDomain optional vertex domain
 * @param lineAttributes optional line-attributes type
 * @param overlap optional WITHOUT OVERLAPS threshold
 */
public record LineTypeSyntax(SyntaxNodeId id, SourceRange range, GeometryKind geometryKind,
                             Optional<String> lineForm, Optional<NameSyntax> vertexDomain,
                             Optional<String> lineAttributes, Optional<String> overlap)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public LineTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(geometryKind, "geometryKind");
        lineForm = Objects.requireNonNull(lineForm, "lineForm");
        vertexDomain = Objects.requireNonNull(vertexDomain, "vertexDomain");
        lineAttributes = Objects.requireNonNull(lineAttributes, "lineAttributes");
        overlap = Objects.requireNonNull(overlap, "overlap");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.TYPE_LINE;
    }

    @Override
    public List<SyntaxNode> children() {
        return vertexDomain.<List<SyntaxNode>>map(List::of).orElseGet(List::of);
    }
}
