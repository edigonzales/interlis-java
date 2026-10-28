package guru.interlis.syntax;

import guru.interlis.model.Modifier;
import guru.interlis.model.Cardinality;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * ASSOCIATION declaration.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param nameRange the {@code nameRange} component
 * @param endName optional name after END
 * @param baseAssociation optional base association
 * @param modifiers modifiers
 * @param roles role identifiers
 * @param attributes attribute identifiers
 * @param constraints the {@code constraints} component
 * @param cardinality declared association-instance multiplicity; absent means exactly one
 * @param oidRules declared OID rules in source order
 */
public record AssociationDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                           Optional<SourceRange> nameRange,
                                           Optional<String> endName,
                                           Optional<NameSyntax> baseAssociation,
                                           Set<Modifier> modifiers,
                                           List<RoleDeclarationSyntax> roles,
                                           List<AttributeDeclarationSyntax> attributes,
                                           List<ConstraintDeclarationSyntax> constraints,
                                           List<OidRuleSyntax> oidRules, Optional<Cardinality> cardinality, Optional<ViewDeclarationSyntax.Base> derivedFrom)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public AssociationDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(derivedFrom);
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        endName = Objects.requireNonNull(endName, "endName");
        baseAssociation = Objects.requireNonNull(baseAssociation, "baseAssociation");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        oidRules = List.copyOf(Objects.requireNonNull(oidRules, "oidRules"));
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.ASSOCIATION;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>();
        baseAssociation.ifPresent(result::add);
        derivedFrom.ifPresent(b -> { result.add(b.alias()); result.add(b.target()); });
        oidRules.forEach(rule -> rule.domain().ifPresent(result::add));
        result.addAll(roles);
        result.addAll(attributes);
        result.addAll(constraints);
        return result;
    }
}
