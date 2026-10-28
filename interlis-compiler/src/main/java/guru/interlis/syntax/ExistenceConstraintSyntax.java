package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/** EXISTENCE CONSTRAINT source REQUIRED IN target:path [OR ...]. */
public record ExistenceConstraintSyntax(SyntaxNodeId id, SourceRange range,
                                        ExpressionSyntax source,
                                        List<ExistenceTargetSyntax> targets)
        implements SyntaxNode {
    /** Creates a validated instance. */
    public ExistenceConstraintSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(source, "source");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }

    /** One alternative target. */
    public record ExistenceTargetSyntax(NameSyntax viewable, ExpressionSyntax path) {
        /** Creates a validated instance. */
        public ExistenceTargetSyntax {
            Objects.requireNonNull(viewable, "viewable");
            Objects.requireNonNull(path, "path");
        }
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.CONSTRAINT;
    }

    @Override
    public List<SyntaxNode> children() {
        var children = new java.util.ArrayList<SyntaxNode>();
        children.add(source);
        for (var target : targets) { children.add(target.viewable()); children.add(target.path()); }
        return List.copyOf(children);
    }
}
