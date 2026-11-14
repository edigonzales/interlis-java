package guru.interlis.compiler;

import guru.interlis.source.ContentFingerprint;
import guru.interlis.source.SourceId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Complete immutable input of one compiler run.
 *
 * @param roots the {@code roots} component
 * @param sources source identifiers
 * @param options options
 */
public record CompilationRequest(List<ModelSelector> roots, SourceSet sources,
                                 CompilerOptions options) {
    /** Creates a validated instance. */
    public CompilationRequest {
        roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root selector is required");
        }
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(options, "options");
    }
    /** Returns the expected provenance of an analysis produced from this request. */
    public AnalysisProvenance provenance() {
        var sourceFingerprints = new LinkedHashMap<SourceId, ContentFingerprint>();
        sources.sources().forEach((id, text) -> sourceFingerprints.put(id, text.contentFingerprint()));
        return new AnalysisProvenance(
                guru.interlis.compiler.internal.Fingerprints.COMPILER_VERSION,
                guru.interlis.compiler.internal.Fingerprints.GRAMMAR_VERSION,
                options.semanticProfile().value(), sources.fingerprint().value(), options.fingerprint(), sourceFingerprints);
    }

    /**
     * Returns the canonical identity of actual roots, texts, import bindings,
     * resolver provenance and options, independent of reuse or parse mode.
     * The claimed resolver digest alone is never used as a cache certificate.
     */
    public CompilationFingerprint fingerprint() {
        return new guru.interlis.compiler.internal.CompilationFingerprinter().fingerprint(this, AnalysisReuse.none());
    }
}
