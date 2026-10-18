package guru.interlis.projection;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable diagram options; renderer style is separate.
 *
 * @param selection selection
 * @param scope the {@code scope} component
 * @param visibleKinds the {@code visibleKinds} component
 * @param inheritedMembers the {@code inheritedMembers} component
 * @param showTypes the {@code showTypes} component
 * @param showRoleCardinalities the {@code showRoleCardinalities} component
 * @param showConstraints the {@code showConstraints} component
 * @param showImports the {@code showImports} component
 * @param labelPolicy the {@code labelPolicy} component
 * @param associationPresentation the {@code associationPresentation} component
 * @param budget the {@code budget} component
 */
public record DiagramOptions(DiagramSelection selection, DiagramScope scope,
                             Set<DiagramNodeKind> visibleKinds,
                             InheritedMembers inheritedMembers, boolean showTypes,
                             boolean showRoleCardinalities, boolean showConstraints,
                             boolean showImports, LabelPolicy labelPolicy,
                             AssociationPresentation associationPresentation,
                             ProjectionBudget budget) {
    /** Creates a validated instance. */
    public DiagramOptions {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(scope, "scope");
        visibleKinds = Set.copyOf(Objects.requireNonNull(visibleKinds, "visibleKinds"));
        Objects.requireNonNull(inheritedMembers, "inheritedMembers");
        Objects.requireNonNull(labelPolicy, "labelPolicy");
        Objects.requireNonNull(associationPresentation, "associationPresentation");
        Objects.requireNonNull(budget, "budget");
    }

    /**
     * Executes {@code defaults}.
     *
     * @param selection the {@code selection} argument
     *
     * @return the result
     */
    public static DiagramOptions defaults(DiagramSelection selection) {
        return new DiagramOptions(selection, DiagramScope.SELECTED,
                Set.of(DiagramNodeKind.CLASS, DiagramNodeKind.STRUCTURE,
                        DiagramNodeKind.ASSOCIATION, DiagramNodeKind.DOMAIN,
                        DiagramNodeKind.VIEW),
                InheritedMembers.SHOW_WITH_ORIGIN, true, true, true, true,
                LabelPolicy.CANONICAL, AssociationPresentation.ENTITY,
                ProjectionBudget.defaults());
    }

    /**
     * Returns the {@code fingerprint} value.
     *
     * @return the value
     */
    public String fingerprint() {
        return "scope=" + scope + ";kinds=" + visibleKinds.stream().map(Enum::name).sorted().toList()
                + ";inherited=" + inheritedMembers + ";types=" + showTypes
                + ";cardinalities=" + showRoleCardinalities + ";constraints=" + showConstraints
                + ";imports=" + showImports + ";labels=" + labelPolicy
                + ";associations=" + associationPresentation + ";budget=" + budget
                + ";selection=" + selection.symbols().stream()
                .map(s -> s.symbol().value()).toList();
    }
}
