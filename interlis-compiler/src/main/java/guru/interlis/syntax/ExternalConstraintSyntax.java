package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * One constraint inside a CONSTRAINTS OF block, carrying its target viewable.
 *
 * @param id stable identifier
 * @param range source range
 * @param target target class or association
 * @param constraint wrapped constraint declaration
 */
public record ExternalConstraintSyntax(SyntaxNodeId id, SourceRange range, NameSyntax target,
                                       ConstraintDeclarationSyntax constraint)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public ExternalConstraintSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(constraint, "constraint");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.EXTERNAL_CONSTRAINT;
    }

    @Override
    public List<SyntaxNode> children() {
        return List.of(target, constraint);
    }
}
