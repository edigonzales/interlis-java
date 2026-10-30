package guru.interlis.workspace.resolution;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.ModelSelector;

import java.util.List;
import java.util.Objects;

/**
 * Explicit root selection and expected inventory revision.
 *
 * @param roots the {@code roots} component
 * @param policy the {@code policy} component
 * @param budget the {@code budget} component
 * @param expectedInventoryFingerprint the {@code expectedInventoryFingerprint} component
 */
public record ResolutionRequest(List<ModelSelector> roots, ResolutionPolicy policy,
                                AnalysisBudget budget, String expectedInventoryFingerprint,guru.interlis.compiler.SemanticOverlays overlays) {
    public ResolutionRequest(List<ModelSelector> roots,ResolutionPolicy policy,AnalysisBudget budget,String expectedInventoryFingerprint) {
        this(roots,policy,budget,expectedInventoryFingerprint,guru.interlis.compiler.SemanticOverlays.empty());
    }
    /** Creates a validated instance. */
    public ResolutionRequest {
        Objects.requireNonNull(overlays,"overlays");
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root selector is required");
        }
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(expectedInventoryFingerprint, "expectedInventoryFingerprint");
    }
}
