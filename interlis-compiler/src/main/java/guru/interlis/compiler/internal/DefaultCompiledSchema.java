package guru.interlis.compiler.internal;

import guru.interlis.model.CompiledSchema;
import guru.interlis.model.LanguageVersion;
import guru.interlis.model.SchemaFingerprint;
import guru.interlis.model.SchemaProvenance;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.query.DefaultSemanticQueries;
import guru.interlis.model.query.SemanticQueries;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Final immutable checked schema selection; produced only by the gate. */
public final class DefaultCompiledSchema implements CompiledSchema {
    private final SnapshotId snapshotId;
    private final SchemaFingerprint fingerprint;
    private final SemanticGraph graph;
    private final SemanticQueries queries;
    private final List<SymbolRef> rootModels;
    private final List<SymbolRef> models;
    private final Set<LanguageVersion> languageVersions;
    private final Map<SourceId, SourceRevision> sourceRevisions;
    private final SchemaProvenance provenance;

    DefaultCompiledSchema(SemanticGraph graph, SchemaFingerprint fingerprint,
                          List<SymbolRef> rootModels, List<SymbolRef> models,
                          SchemaProvenance provenance) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.snapshotId = graph.snapshotId();
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.queries = new DefaultSemanticQueries(graph);
        this.rootModels = List.copyOf(Objects.requireNonNull(rootModels, "rootModels"));
        this.models = List.copyOf(Objects.requireNonNull(models, "models"));
        this.languageVersions = graph.languageVersions();
        this.sourceRevisions = graph.sourceRevisions();
        this.provenance = Objects.requireNonNull(provenance, "provenance");
    }

    @Override
    public SnapshotId snapshotId() {
        return snapshotId;
    }

    @Override
    public SchemaFingerprint fingerprint() {
        return fingerprint;
    }

    @Override
    public SemanticGraph semanticGraph() {
        return graph;
    }

    @Override
    public SemanticQueries queries() {
        return queries;
    }

    @Override
    public List<SymbolRef> rootModels() {
        return rootModels;
    }

    @Override
    public List<SymbolRef> models() {
        return models;
    }

    @Override
    public Set<LanguageVersion> languageVersions() {
        return languageVersions;
    }

    @Override
    public Map<SourceId, SourceRevision> sourceRevisions() {
        return sourceRevisions;
    }

    @Override
    public SchemaProvenance provenance() {
        return provenance;
    }
}
