package guru.interlis.workspace.resolution;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SemanticOverlays;

import java.util.List;
import java.util.Objects;

/**
 * Options for pure model resolution against an immutable inventory.
 *
 * @param roots the {@code roots} component
 * @param policy the {@code policy} component
 * @param budget the {@code budget} component
 * @param overlays semantic overlays for the selected language profile
 */
public record ResolutionRequest(List<ModelSelector> roots, ResolutionPolicy policy,
                                AnalysisBudget budget, SemanticOverlays overlays) {
    /** Creates a validated instance. */
    public ResolutionRequest {
        Objects.requireNonNull(overlays,"overlays");
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root selector is required");
        }
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(budget, "budget");
    }
}
