package guru.interlis.syntax;

import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Constraint declaration with an optional typed expression.
 *
 * @param id stable identifier
 * @param range source range
 * @param name declared name
 * @param constraintKind the {@code constraintKind} component
 * @param expression the {@code expression} component
 * @param uniqueKeys structured key paths in source order
 * @param existence optional existence-constraint payload
 * @param localPrefix structure path selecting the LOCAL element scope
 * @param basket whether UNIQUE or SET is restricted to a basket
 * @param plausibility comparison and exact threshold, present for a complete plausibility constraint
 * @param where SET precondition evaluated for each individual object, before its set condition
 */
public record ConstraintDeclarationSyntax(SyntaxNodeId id, SourceRange range,
                                          Optional<String> name, ConstraintKind constraintKind,
                                          Optional<ExpressionSyntax> expression,
                                          List<ExpressionSyntax> uniqueKeys,
                                          Optional<ExistenceConstraintSyntax> existence,
                                          Optional<PathExpressionSyntax> localPrefix, boolean basket,
                                          Optional<Plausibility> plausibility, Optional<ExpressionSyntax> where)
        implements DeclarationSyntax {
    /** Creates a validated instance. */
    public ConstraintDeclarationSyntax {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(range, "range");
        name = Objects.requireNonNull(name, "name");
        Objects.requireNonNull(constraintKind, "constraintKind");
        expression = Objects.requireNonNull(expression, "expression");
        uniqueKeys = List.copyOf(Objects.requireNonNull(uniqueKeys, "uniqueKeys"));
        existence = Objects.requireNonNull(existence, "existence");
        localPrefix = Objects.requireNonNull(localPrefix, "localPrefix");
        plausibility=Objects.requireNonNull(plausibility,"plausibility");where=Objects.requireNonNull(where,"where");
        if(plausibility.isPresent() && constraintKind!=ConstraintKind.PLAUSIBILITY || where.isPresent() && constraintKind!=ConstraintKind.SET)
            throw new IllegalArgumentException("constraint payload belongs to another family");
    }

    /** Source percentage, without rounding or evaluation; original threshold range retained. */
    public record Plausibility(guru.interlis.model.DecimalLiteral percentage,
            guru.interlis.model.BinaryOperator comparison, SourceRange range) {
        public Plausibility {
            Objects.requireNonNull(percentage);Objects.requireNonNull(comparison);Objects.requireNonNull(range);
            if(comparison!=guru.interlis.model.BinaryOperator.LTE && comparison!=guru.interlis.model.BinaryOperator.GTE)
                throw new IllegalArgumentException("plausibility comparison must be <= or >=");
        }
    }

    @Override
    public SyntaxKind kind() {
        return SyntaxKind.CONSTRAINT;
    }

    @Override
    public List<SyntaxNode> children() {
        var children = new java.util.ArrayList<SyntaxNode>();
        where.ifPresent(children::add);
        expression.ifPresent(children::add);
        localPrefix.ifPresent(children::add);
        children.addAll(uniqueKeys);
        existence.ifPresent(children::add);
        return List.copyOf(children);
    }
}
