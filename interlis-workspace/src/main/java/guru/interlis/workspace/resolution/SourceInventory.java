package guru.interlis.workspace.resolution;

import guru.interlis.compiler.ResolutionFingerprint;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.workspace.WorkspaceRevision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable inventory of workspace texts and parsed model headers. */
public final class SourceInventory {
    private final WorkspaceRevision workspaceRevision;
    private final Map<SourceId, SourceText> sources;
    private final Map<SourceId, SyntaxSnapshot> syntax;
    private final List<ModelHeader> headers;
    private final ResolverCatalog catalog;
    private final ResolutionFingerprint fingerprint;

    /**
     * Copies the supplied sources and matching syntax without parsing or performing IO.
     * Headers follow source-ID order, then declaration order. Every source requires a
     * non-null snapshot with exactly the same ID, revision and text.
     *
     * @throws IllegalArgumentException if a snapshot belongs to a different source state
     * @throws NullPointerException if an argument, source or required snapshot is null
     */
    public SourceInventory(WorkspaceRevision workspaceRevision, Map<SourceId, SourceText> sources,
                           ResolverCatalog catalog, Map<SourceId, SyntaxSnapshot> syntax) {
        this.workspaceRevision = Objects.requireNonNull(workspaceRevision, "workspaceRevision");
        this.sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Map<SourceId, SyntaxSnapshot> parsed = new LinkedHashMap<>();
        List<ModelHeader> headers = new ArrayList<>();
        Objects.requireNonNull(syntax, "syntax");
        List<SourceId> ids = new ArrayList<>(sources.keySet());
        ids.sort(java.util.Comparator.comparing(SourceId::value));
        for (SourceId id : ids) {
            SourceText text = sources.get(id);
            SyntaxSnapshot snapshot = Objects.requireNonNull(syntax.get(id), "missing syntax");
            if (!snapshot.source().id().equals(id)
                    || !snapshot.source().revision().equals(text.revision())
                    || !snapshot.source().text().equals(text.text())) {
                throw new IllegalArgumentException("syntax does not match inventory source");
            }
            parsed.put(id, snapshot);
            for (var model : snapshot.root().models()) {
                headers.add(ModelHeader.of(id, text.revision(), model));
            }
        }
        this.syntax = Map.copyOf(parsed);
        this.headers = List.copyOf(headers);
        this.fingerprint = new ResolutionFingerprint(computeFingerprint(sources, catalog));
    }

    private static String computeFingerprint(Map<SourceId,SourceText> sources,ResolverCatalog catalog) {
        StringBuilder canonical=new StringBuilder("inventory-v2");
        field(canonical,Integer.toString(sources.size()));
        sources.entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(SourceId::value))).forEach(e->{
            field(canonical,e.getKey().value());field(canonical,Long.toString(e.getValue().revision().value()));
            field(canonical,e.getValue().contentFingerprint().value());
        });
        field(canonical,catalog.fingerprint());
        return "sha256:"+guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digestUtf16(canonical.toString()));
    }
    private static void field(StringBuilder b,String value){b.append(value.length()).append(':').append(value);}

    /**
     * Returns the {@code workspaceRevision} value.
     *
     * @return the value
     */
    public WorkspaceRevision workspaceRevision() {
        return workspaceRevision;
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
     * Returns the {@code syntax} value.
     *
     * @return the value
     */
    public Map<SourceId, SyntaxSnapshot> syntax() {
        return syntax;
    }

    /**
     * Returns the {@code headers} value.
     *
     * @return the value
     */
    public List<ModelHeader> headers() {
        return headers;
    }

    /**
     * Returns the {@code catalog} value.
     *
     * @return the value
     */
    public ResolverCatalog catalog() {
        return catalog;
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
