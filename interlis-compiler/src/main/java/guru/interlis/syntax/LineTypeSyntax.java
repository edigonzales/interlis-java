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
 * @param lineForms curve form references in declaration order
 * @param vertexDomain optional vertex domain
 * @param lineAttributes optional line-attributes type
 * @param overlap optional WITHOUT OVERLAPS threshold
 */
public record LineTypeSyntax(SyntaxNodeId id, SourceRange range, GeometryKind geometryKind,
                             List<NameSyntax> lineForms, Optional<NameSyntax> vertexDomain,
                             Optional<NameSyntax> lineAttributes, Optional<String> overlap, boolean directed, boolean withoutOverlaps)
        implements TypeSyntax {
    /** Creates a validated instance. */
    public LineTypeSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(geometryKind, "geometryKind");
        lineForms = List.copyOf(lineForms);
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
        java.util.ArrayList<SyntaxNode> children = new java.util.ArrayList<>(lineForms);
        vertexDomain.ifPresent(children::add); lineAttributes.ifPresent(children::add); return List.copyOf(children);
    }
}
