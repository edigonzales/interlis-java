package guru.interlis.projection;

import guru.interlis.model.SnapshotId;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable renderer-independent diagram document version 1.0.
 *
 * @param schemaVersion the {@code schemaVersion} component
 * @param snapshotId snapshot identifier
 * @param sourceRevisions the {@code sourceRevisions} component
 * @param inputKind the {@code inputKind} component
 * @param completeness completeness state
 * @param selection selection
 * @param optionsFingerprint the {@code optionsFingerprint} component
 * @param containers the {@code containers} component
 * @param nodes diagram nodes
 * @param edges diagram edges
 * @param notices projection notices
 */
public record DiagramDocument(String schemaVersion, SnapshotId snapshotId,
                              Map<SourceId, SourceRevision> sourceRevisions,
                              InputKind inputKind, ProjectionCompleteness completeness,
                              List<SymbolRef> selection, String optionsFingerprint,
                              List<DiagramContainer> containers, List<DiagramNode> nodes,
                              List<DiagramEdge> edges, List<ProjectionNotice> notices) {
    /** Creates a validated instance. */
    public DiagramDocument {
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(snapshotId, "snapshotId");
        sourceRevisions = Map.copyOf(Objects.requireNonNull(sourceRevisions, "sourceRevisions"));
        Objects.requireNonNull(inputKind, "inputKind");
        Objects.requireNonNull(completeness, "completeness");
        selection = List.copyOf(Objects.requireNonNull(selection, "selection"));
        Objects.requireNonNull(optionsFingerprint, "optionsFingerprint");
        containers = List.copyOf(Objects.requireNonNull(containers, "containers"));
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
    }

    public static final String SCHEMA_VERSION = "1.0";
}
