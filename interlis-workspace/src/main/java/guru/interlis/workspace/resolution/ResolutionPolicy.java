package guru.interlis.workspace.resolution;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.source.SourceId;
import guru.interlis.source.Hex;
import guru.interlis.source.Sha256;
import java.util.*;

/**
 * Immutable exact selections and source priority groups for the pure resolver.
 * Lower ranks win; absent ranks denote workspace sources (rank -1 when preferred,
 * otherwise rank 0). Different definitions in the winning group remain ambiguous.
 * Version strings are compared exactly, never ordered as dates or semantic versions.
 */
public record ResolutionPolicy(boolean preferWorkspaceSource,
                               Map<String, ModelSelector.ByName> modelSelections,
                               Map<SourceId, Integer> sourcePriorities) {
    /** Compatibility constructor with no pins or external priority groups. */
    public ResolutionPolicy(boolean preferWorkspaceSource) {
        this(preferWorkspaceSource, Map.of(), Map.of());
    }

    public ResolutionPolicy {
        modelSelections = ownedMap(modelSelections, "modelSelections");
        sourcePriorities = ownedMap(sourcePriorities, "sourcePriorities");
        modelSelections.forEach((name, selector) -> {
            if (!name.equals(selector.modelName())) throw new IllegalArgumentException("selection key differs from model name");
        });
        sourcePriorities.values().forEach(rank -> {
            if (rank < 0) throw new IllegalArgumentException("repository priority must be nonnegative");
        });
    }

    private static <K,V> Map<K,V> ownedMap(Map<K,V> input, String name) {
        Objects.requireNonNull(input, name).forEach((key, value) -> {
            Objects.requireNonNull(key, "map key"); Objects.requireNonNull(value, "map value");
        });
        // TeaVM 0.16.0's Map.copyOf(empty).get(nonNullKey) divides by zero in WasmGC.
        // An owned linked map preserves the same immutable contract on every backend.
        return Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    public static ResolutionPolicy defaults() { return new ResolutionPolicy(true); }

    /**
     * Adds configured restrictions only where the requested selector is unqualified.
     * Explicit restrictions always remain in force; select also checks configured
     * restrictions, so conflicting explicit/configured selectors have no candidates.
     */
    public ModelSelector.ByName qualify(ModelSelector.ByName query) {
        Objects.requireNonNull(query, "query");
        var configured = modelSelections.get(query.modelName());
        if (configured == null) return query;
        return new ModelSelector.ByName(query.modelName(),
                query.modelVersion().or(() -> configured.modelVersion()),
                query.sourceHint().or(() -> configured.sourceHint()));
    }

    /**
     * Selects actual parsed headers without IO. Input order never breaks ambiguity.
     * The caller must bound and charge the scan and check cancellation.
     */
    public List<ModelHeader> select(ModelSelector.ByName query, List<ModelHeader> headers) {
        Objects.requireNonNull(query, "query");
        var configured = modelSelections.get(query.modelName());
        var matching = headers.stream().filter(h -> matches(query, h))
                .filter(h -> configured == null || matches(configured, h))
                .sorted(Comparator.comparing((ModelHeader h) -> h.source().value())
                        .thenComparingInt(ModelHeader::declarationOffset)).toList();
        int priority = matching.stream().mapToInt(h -> rank(h.source())).min().orElse(0);
        return matching.stream().filter(h -> rank(h.source()) == priority).toList();
    }

    private int rank(SourceId source) {
        return sourcePriorities.getOrDefault(source, preferWorkspaceSource ? -1 : 0);
    }

    private static boolean matches(ModelSelector.ByName query, ModelHeader header) {
        return query.modelName().equals(header.modelName())
                && query.modelVersion().filter(v -> !v.equals(header.modelVersion())).isEmpty()
                && query.sourceHint().filter(s -> !s.equals(header.source())).isEmpty();
    }

    /** Canonical content identity; map iteration and separators cannot change it. */
    public String fingerprint() {
        var b = new StringBuilder("resolution-policy-v2");
        field(b, Boolean.toString(preferWorkspaceSource));
        field(b, Integer.toString(modelSelections.size()));
        modelSelections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            field(b, e.getKey());
            optional(b, e.getValue().modelVersion());
            optional(b, e.getValue().sourceHint().map(SourceId::value));
        });
        field(b, Integer.toString(sourcePriorities.size()));
        sourcePriorities.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().value())).forEach(e -> {
            field(b, e.getKey().value()); field(b, e.getValue().toString());
        });
        return "sha256:" + Hex.encode(Sha256.digestUtf16(b.toString()));
    }
    private static void field(StringBuilder b, String s) { b.append(s.length()).append(':').append(s); }
    private static void optional(StringBuilder b, Optional<String> s) {
        field(b, s.isPresent() ? "present" : "absent"); s.ifPresent(v -> field(b, v));
    }
}
