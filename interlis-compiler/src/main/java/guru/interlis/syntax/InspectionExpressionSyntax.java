package guru.interlis.syntax;

import guru.interlis.source.SourceRange;
import java.util.*;

/** Named or inline inspection, optionally restricted to one source object.
 * Immutable, null-free; exactly one of view and formation is present.
 * Children retain source/revision-bound UTF-16 ranges, in source order. */
public record InspectionExpressionSyntax(SyntaxNodeId id, SourceRange range,
        Optional<NameSyntax> view, Optional<ViewDeclarationSyntax.Formation> formation,
        Optional<ExpressionSyntax> restriction) implements ExpressionSyntax {
    public InspectionExpressionSyntax {
        Objects.requireNonNull(id); Objects.requireNonNull(range); Objects.requireNonNull(view);
        Objects.requireNonNull(formation); Objects.requireNonNull(restriction);
        if (view.isPresent() == formation.isPresent()) throw new IllegalArgumentException("one inspection source required");
        formation.ifPresent(f -> {
            if ((f.kind() != ViewDeclarationSyntax.FormationKind.INSPECTION
                    && f.kind() != ViewDeclarationSyntax.FormationKind.AREA_INSPECTION)
                    || f.bases().size() != 1 || f.paths().size() != 1)
                throw new IllegalArgumentException("one inspection basis and path required");
        });
    }
    @Override public SyntaxKind kind() { return SyntaxKind.EXPRESSION_INSPECTION; }
    @Override public List<SyntaxNode> children() {
        List<SyntaxNode> children = new ArrayList<>(); view.ifPresent(children::add);
        formation.ifPresent(f -> { f.bases().forEach(b -> { children.add(b.alias()); children.add(b.target()); }); children.addAll(f.paths()); });
        restriction.ifPresent(children::add); return List.copyOf(children);
    }
}
