package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Line form declaration with the exact structure reference range. */
public record LineFormDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
        Optional<SourceRange> nameRange, NameSyntax segmentStructure) implements DeclarationSyntax {
    public LineFormDeclarationSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(name);
        Objects.requireNonNull(nameRange); Objects.requireNonNull(segmentStructure);
    }
    @Override public SyntaxKind kind() { return SyntaxKind.LINE_FORM; }
    @Override public List<SyntaxNode> children() { return List.of(segmentStructure); }
}
