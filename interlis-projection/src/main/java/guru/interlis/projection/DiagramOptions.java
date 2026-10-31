package guru.interlis.projection;

import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalInt;
import guru.interlis.model.SymbolRef;

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
 * @param translationModel optional translation model in the input snapshot; labels use its bound correspondences, identities and source anchors stay canonical
 * @param neighbourhoodDepth optional maximum relationship distance, 0..32; required by the NEIGHBOURHOOD scope
 * @param budget the {@code budget} component
 */
public record DiagramOptions(DiagramSelection selection, DiagramScope scope,
                             Set<DiagramNodeKind> visibleKinds,
                             InheritedMembers inheritedMembers, boolean showTypes,
                             boolean showRoleCardinalities, boolean showConstraints,
                             boolean showImports, LabelPolicy labelPolicy,
                             AssociationPresentation associationPresentation,
                             ProjectionBudget budget, Optional<SymbolRef> translationModel,
                             OptionalInt neighbourhoodDepth) {
    /** Creates a validated instance. */
    public DiagramOptions {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(scope, "scope");
        visibleKinds = Set.copyOf(Objects.requireNonNull(visibleKinds, "visibleKinds"));
        Objects.requireNonNull(inheritedMembers, "inheritedMembers");
        Objects.requireNonNull(labelPolicy, "labelPolicy");
        Objects.requireNonNull(associationPresentation, "associationPresentation");
        Objects.requireNonNull(budget, "budget");
        translationModel=Objects.requireNonNull(translationModel,"translationModel");
        neighbourhoodDepth=Objects.requireNonNull(neighbourhoodDepth,"neighbourhoodDepth");
        if(neighbourhoodDepth.isPresent() && (neighbourhoodDepth.getAsInt()<0 || neighbourhoodDepth.getAsInt()>32))
            throw new IllegalArgumentException("neighbourhood depth must be in 0..32");
        if(scope!=DiagramScope.NEIGHBOURHOOD && neighbourhoodDepth.isPresent())throw new IllegalArgumentException("depth requires NEIGHBOURHOOD");
        if(labelPolicy!=LabelPolicy.TRANSLATED && translationModel.isPresent())throw new IllegalArgumentException("translation model requires TRANSLATED labels");
    }

    /** Compatibility constructor without explicit translation/neighbourhood parameters.
     * Those modes produce a typed rejection until their required selection is supplied. */
    public DiagramOptions(DiagramSelection selection,DiagramScope scope,Set<DiagramNodeKind> visibleKinds,
            InheritedMembers inheritedMembers,boolean showTypes,boolean showRoleCardinalities,boolean showConstraints,
            boolean showImports,LabelPolicy labelPolicy,AssociationPresentation associationPresentation,ProjectionBudget budget) {
        this(selection,scope,visibleKinds,inheritedMembers,showTypes,showRoleCardinalities,showConstraints,showImports,
                labelPolicy,associationPresentation,budget,Optional.empty(),OptionalInt.empty());
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
                        DiagramNodeKind.ASSOCIATION, DiagramNodeKind.DOMAIN, DiagramNodeKind.LINE_FORM, DiagramNodeKind.METADATA_BASKET, DiagramNodeKind.METAOBJECT, DiagramNodeKind.GRAPHIC, DiagramNodeKind.DRAWING_RULE,
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
                + translationModel.map(ref -> ";translation="+ref.snapshot().value()+":"+ref.symbol().value()).orElse("")
                + (neighbourhoodDepth.isPresent()?";depth="+neighbourhoodDepth.getAsInt():"")
                + ";selection=" + selection.symbols().stream()
                .map(s -> s.symbol().value()).toList();
    }
}
