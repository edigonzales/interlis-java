package guru.interlis.model;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only checked schema view. Instances produced by this repository's
 * engine exist only after the CompilationGate accepted the requested closure.
 */
public interface CompiledSchema {
    /**
     * Returns the {@code snapshotId} value.
     *
     * @return the value
     */
    SnapshotId snapshotId();

    /**
     * Returns the {@code fingerprint} value.
     *
     * @return the value
     */
    SchemaFingerprint fingerprint();

    /**
     * Returns the {@code semanticGraph} value.
     *
     * @return the value
     */
    SemanticGraph semanticGraph();

    /**
     * Returns the {@code queries} value.
     *
     * @return the value
     */
    guru.interlis.model.query.SemanticQueries queries();

    /**
     * Returns the {@code rootModels} value.
     *
     * @return the value
     */
    List<SymbolRef> rootModels();

    /**
     * Returns the {@code models} value.
     *
     * @return the value
     */
    List<SymbolRef> models();

    /**
     * Returns the {@code languageVersions} value.
     *
     * @return the value
     */
    Set<LanguageVersion> languageVersions();

    /**
     * Returns the {@code sourceRevisions} value.
     *
     * @return the value
     */
    Map<SourceId, SourceRevision> sourceRevisions();

    /**
     * Returns the {@code provenance} value.
     *
     * @return the value
     */
    SchemaProvenance provenance();
}
