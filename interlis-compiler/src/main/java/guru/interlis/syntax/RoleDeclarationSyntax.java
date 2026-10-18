package guru.interlis.syntax;

import guru.interlis.model.Cardinality;
import guru.interlis.model.Modifier;
import guru.interlis.model.RoleAggregation;
import guru.interlis.source.SourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Association role.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param nameRange the {@code nameRange} component
 * @param cardinality cardinality
 * @param target target symbol
 * @param modifiers modifiers
 * @param aggregation role aggregation kind
 * @param explicitCardinality whether a cardinality was written in source
 */
public record RoleDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                    Optional<SourceRange> nameRange, Cardinality cardinality,
                                    NameSyntax target, Set<Modifier> modifiers,
                                    RoleAggregation aggregation, boolean explicitCardinality)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public RoleDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(cardinality, "cardinality");
        Objects.requireNonNull(target, "target");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        Objects.requireNonNull(aggregation, "aggregation");
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.ROLE;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>();
        result.add(target);
        return result;
    }
}
