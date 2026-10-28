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
 * @param targets ordered target alternatives, each retaining base and restrictions
 * @param modifiers modifiers
 * @param aggregation role aggregation kind
 * @param explicitCardinality whether a cardinality was written in source
 */
public record RoleDeclarationSyntax(SyntaxNodeId id, SourceRange range, String name,
                                    Optional<SourceRange> nameRange, Cardinality cardinality,
                                    List<Target> targets, Set<Modifier> modifiers,
                                    RoleAggregation aggregation, boolean explicitCardinality, Optional<ExpressionSyntax> derivation)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public RoleDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(derivation);
        Objects.requireNonNull(range, "range");
        Objects.requireNonNull(name, "name");
        nameRange = Objects.requireNonNull(nameRange, "nameRange");
        Objects.requireNonNull(cardinality, "cardinality");
        targets = List.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("role needs targets");
        modifiers = Set.copyOf(Objects.requireNonNull(modifiers, "modifiers"));
        Objects.requireNonNull(aggregation, "aggregation");
    }

    /** Non-null immutable alternative; names retain exact source/revision ranges. */
    public record Target(NameSyntax base, List<NameSyntax> restrictions) {
        public Target {
            Objects.requireNonNull(base, "base");
            restrictions = List.copyOf(restrictions);
        }
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.ROLE;
    }

    @Override
    public List<SyntaxNode> children() {
        List<SyntaxNode> result = new ArrayList<>();
        for (Target target : targets) {
            result.add(target.base());
            result.addAll(target.restrictions());
        }
        return result;
    }
}
