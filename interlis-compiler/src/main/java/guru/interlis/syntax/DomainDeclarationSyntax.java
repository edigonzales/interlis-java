package guru.interlis.syntax;

import guru.interlis.model.Modifier;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * DOMAIN declaration with its declared type.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param nameRange the {@code nameRange} component
 * @param modifiers modifiers
 * @param baseDomain the {@code baseDomain} component
 * @param type type reference
 * @param constraints the {@code constraints} component
 */
public record DomainDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                      Optional<SourceRange> nameRange, Set<Modifier> modifiers,
                                      Optional<NameSyntax> baseDomain, TypeSyntax type,
                                      List<ConstraintDeclarationSyntax> constraints)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public DomainDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        baseDomain = Objects.requireNonNull(baseDomain, "baseDomain");
        Objects.requireNonNull(type, "type");
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.DOMAIN;
    }

    @Override
    public List<SyntaxNode> children() {
        java.util.ArrayList<SyntaxNode> result = new java.util.ArrayList<>();
        baseDomain.ifPresent(result::add);
        result.add(type);
        result.addAll(constraints);
        return result;
    }
}
