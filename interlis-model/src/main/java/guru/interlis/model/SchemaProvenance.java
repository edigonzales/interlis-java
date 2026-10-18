package guru.interlis.model;

import guru.interlis.source.ContentFingerprint;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reproducible provenance of a checked schema.
 *
 * @param compilerVersion the {@code compilerVersion} component
 * @param grammarVersion the {@code grammarVersion} component
 * @param semanticProfile the {@code semanticProfile} component
 * @param roots the {@code roots} component
 * @param sources source identifiers
 * @param resolverFingerprint the {@code resolverFingerprint} component
 * @param optionsFingerprint the {@code optionsFingerprint} component
 */
public record SchemaProvenance(String compilerVersion, String grammarVersion,
                               String semanticProfile, List<ModelSelectorView> roots,
                               Map<guru.interlis.source.SourceId, ContentFingerprint> sources,
                               String resolverFingerprint, String optionsFingerprint) {
    /** Creates a validated instance. */
    public SchemaProvenance {
        Objects.requireNonNull(compilerVersion, "compilerVersion");
        Objects.requireNonNull(grammarVersion, "grammarVersion");
        Objects.requireNonNull(semanticProfile, "semanticProfile");
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(resolverFingerprint, "resolverFingerprint");
        Objects.requireNonNull(optionsFingerprint, "optionsFingerprint");
    }

    /**
     * Immutable value.
     *
     * @param kind kind discriminator
     * @param modelName the {@code modelName} component
     * @param modelVersion model version
     * @param source source identifier
     */
    public record ModelSelectorView(String kind, String modelName,
                                    java.util.Optional<String> modelVersion,
                                    java.util.Optional<String> source) {
        /** Creates a validated instance. */
        public ModelSelectorView {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(modelName, "modelName");
            modelVersion = Objects.requireNonNull(modelVersion, "modelVersion");
            source = Objects.requireNonNull(source, "source");
        }
    }
}
