package guru.interlis.compiler;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable source and binding set handed to the engine. */
public final class SourceSet {
    private final Map<SourceId, SourceText> sources;
    private final List<ImportBinding> imports;
    private final List<Diagnostic> resolutionDiagnostics;
    private final ResolutionFingerprint fingerprint;
    private final List<TranslationBinding> translations;

    private SourceSet(Map<SourceId, SourceText> sources, List<ImportBinding> imports,
                      List<Diagnostic> resolutionDiagnostics, ResolutionFingerprint fingerprint, List<TranslationBinding> translations) {
        this.sources = Map.copyOf(sources);
        this.imports = List.copyOf(imports);
        this.resolutionDiagnostics = List.copyOf(resolutionDiagnostics);
        this.fingerprint = fingerprint;
        this.translations = List.copyOf(translations);
    }

    public static SourceSet of(Map<SourceId, SourceText> sources, List<ImportBinding> imports,
                               List<Diagnostic> resolutionDiagnostics,
                               ResolutionFingerprint fingerprint) {
        return of(sources, imports, resolutionDiagnostics, fingerprint, List.of());
    }

    /** Copies all inputs, including exact original selections rechecked by the compiler. */
    public static SourceSet of(Map<SourceId, SourceText> sources, List<ImportBinding> imports,
                               List<Diagnostic> resolutionDiagnostics, ResolutionFingerprint fingerprint,
                               List<TranslationBinding> translations) {
        Objects.requireNonNull(sources, "sources");
        Map<SourceId, SourceText> copy = new LinkedHashMap<>();
        for (Map.Entry<SourceId, SourceText> entry : sources.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "source key");
            SourceText text = Objects.requireNonNull(entry.getValue(), "source text");
            if (!entry.getKey().equals(text.id())) {
                throw new IllegalArgumentException("source map key does not match SourceText.id()");
            }
            copy.put(entry.getKey(), text);
        }
        return new SourceSet(copy, Objects.requireNonNull(imports, "imports"),
                Objects.requireNonNull(resolutionDiagnostics, "resolutionDiagnostics"),
                Objects.requireNonNull(fingerprint, "fingerprint"), Objects.requireNonNull(translations, "translations"));
    }

    /** Returns the immutable resolver selections for source/configured translation originals. */
    public List<TranslationBinding> translations() { return translations; }

    /**
     * Creates a source set without imports or resolver diagnostics.
     *
     * @param sources source texts by id
     * @return immutable source set
     */
    public static SourceSet of(Map<SourceId, SourceText> sources) {
        return of(sources, List.of(), List.of(), ResolutionFingerprint.none());
    }

    /**
     * Returns the {@code sources} value.
     *
     * @return the value
     */
    public Map<SourceId, SourceText> sources() {
        return sources;
    }

    /**
     * Returns the {@code imports} value.
     *
     * @return the value
     */
    public List<ImportBinding> imports() {
        return imports;
    }

    /**
     * Returns the {@code resolutionDiagnostics} value.
     *
     * @return the value
     */
    public List<Diagnostic> resolutionDiagnostics() {
        return resolutionDiagnostics;
    }

    /**
     * Returns the {@code fingerprint} value.
     *
     * @return the value
     */
    public ResolutionFingerprint fingerprint() {
        return fingerprint;
    }
}
