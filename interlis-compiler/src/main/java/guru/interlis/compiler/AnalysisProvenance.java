package guru.interlis.compiler;

import guru.interlis.source.ContentFingerprint;
import guru.interlis.source.SourceId;

import java.util.Map;
import java.util.Objects;

/**
 * Reproducible provenance for one analysis snapshot.
 *
 * @param compilerVersion the {@code compilerVersion} component
 * @param grammarVersion the {@code grammarVersion} component
 * @param semanticProfile the {@code semanticProfile} component
 * @param resolverFingerprint the {@code resolverFingerprint} component
 * @param optionsFingerprint the {@code optionsFingerprint} component
 * @param sourceFingerprints the {@code sourceFingerprints} component
 */
public record AnalysisProvenance(String compilerVersion, String grammarVersion,
                                 String semanticProfile, String resolverFingerprint,
                                 String optionsFingerprint,
                                 Map<SourceId, ContentFingerprint> sourceFingerprints) {
    /** Creates a validated instance. */
    public AnalysisProvenance {
        Objects.requireNonNull(compilerVersion, "compilerVersion");
        Objects.requireNonNull(grammarVersion, "grammarVersion");
        Objects.requireNonNull(semanticProfile, "semanticProfile");
        Objects.requireNonNull(resolverFingerprint, "resolverFingerprint");
        Objects.requireNonNull(optionsFingerprint, "optionsFingerprint");
        sourceFingerprints = Map.copyOf(
                Objects.requireNonNull(sourceFingerprints, "sourceFingerprints"));
    }
}
