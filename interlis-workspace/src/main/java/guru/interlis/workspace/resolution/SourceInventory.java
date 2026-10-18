package guru.interlis.workspace.resolution;

import guru.interlis.compiler.AnalysisBudget;
import guru.interlis.compiler.ResolutionFingerprint;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxService;
import guru.interlis.syntax.SyntaxServices;
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

    public SourceInventory(WorkspaceRevision workspaceRevision, Map<SourceId, SourceText> sources,
                           ResolverCatalog catalog, AnalysisBudget budget) {
        this.workspaceRevision = Objects.requireNonNull(workspaceRevision, "workspaceRevision");
        this.sources = Map.copyOf(Objects.requireNonNull(sources, "sources"));
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Map<SourceId, SyntaxSnapshot> parsed = new LinkedHashMap<>();
        List<ModelHeader> headers = new ArrayList<>();
        SyntaxService service = SyntaxServices.create();
        List<SourceId> ids = new ArrayList<>(sources.keySet());
        ids.sort(java.util.Comparator.comparing(SourceId::value));
        for (SourceId id : ids) {
            SourceText text = sources.get(id);
            SyntaxSnapshot snapshot = service.parse(text, ParseOptions.defaults(ParseMode.EDITOR),
                    guru.interlis.execution.CancellationToken.none());
            parsed.put(id, snapshot);
            for (var model : snapshot.root().models()) {
                headers.add(ModelHeader.of(id, text.revision(), model));
            }
        }
        this.syntax = Map.copyOf(parsed);
        this.headers = List.copyOf(headers);
        this.fingerprint = new ResolutionFingerprint(computeFingerprint(workspaceRevision,
                sources, catalog));
    }

    private static String computeFingerprint(WorkspaceRevision revision,
                                             Map<SourceId, SourceText> sources,
                                             ResolverCatalog catalog) {
        StringBuilder canonical = new StringBuilder("inventory|").append(revision.value()).append('|');
        sources.entrySet().stream().sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(SourceId::value)))
                .forEach(e -> canonical.append(e.getKey().value()).append('@')
                        .append(e.getValue().revision().value()).append(':')
                        .append(e.getValue().contentFingerprint().value()).append(';'));
        canonical.append(catalog.fingerprint());
        byte[] digest = guru.interlis.source.Sha256.digest(
                canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "sha256:" + guru.interlis.source.Hex.encode(digest);
    }

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
