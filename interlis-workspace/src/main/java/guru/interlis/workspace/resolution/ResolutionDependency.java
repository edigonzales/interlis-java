package guru.interlis.workspace.resolution;

import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.ResolutionFingerprint;

import java.util.List;
import java.util.Objects;

/**
 * Positive, negative or ambiguous lookup dependency.
 *
 * @param selector the {@code selector} component
 * @param importSites the {@code importSites} component
 * @param candidates the {@code candidates} component
 * @param kind kind discriminator
 * @param fingerprint fingerprint
 */
public record ResolutionDependency(ModelSelector selector, List<ImportSite> importSites,
                                   List<ModelLocation> candidates, Kind kind,
                                   ResolutionFingerprint fingerprint) {
    /** Creates a validated instance. */
    public ResolutionDependency {
        Objects.requireNonNull(selector, "selector");
        importSites = List.copyOf(Objects.requireNonNull(importSites, "importSites"));
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public enum Kind {
        /** The {@code POSITIVE} constant. */
        POSITIVE,
        /** The {@code NEGATIVE} constant. */
        NEGATIVE,
        /** The {@code AMBIGUOUS} constant. */
        AMBIGUOUS
    }
}
