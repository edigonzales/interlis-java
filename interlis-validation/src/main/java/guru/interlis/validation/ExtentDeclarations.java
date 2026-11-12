package guru.interlis.validation;

import java.util.List;
import java.util.Set;

/** Immutable, explicitly declared extents in host declaration order. */
public record ExtentDeclarations(List<ExtentDeclaration> claims) {
    public ExtentDeclarations {
        claims = List.copyOf(claims);
    }

    /** An open dataset with no host claims of completeness. */
    public static ExtentDeclarations open() {
        return new ExtentDeclarations(List.of());
    }

    void check(ValidationPlan plan, Set<InputId> inputs) {
        for (var claim : claims) {
            var selector = claim.selector();
            if (!plan.snapshotId().equals(selector.root().snapshot())
                    || plan.transferPlan().topic(selector.root()).isEmpty()
                        && plan.transferPlan().object(selector.root()).isEmpty()) {
                throw new IllegalArgumentException("Extent root is outside the plan");
            }
            if (!inputs.containsAll(selector.inputs())) {
                throw new IllegalArgumentException("Extent refers to an unknown input");
            }
        }
    }

    boolean explicitlyComplete(ExtentSelector scope, ExtentAspect aspect) {
        return claims.stream().anyMatch(claim -> claim.aspect() == aspect && claim.selector().equals(scope));
    }
}
