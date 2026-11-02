package guru.interlis.lsp;

import java.util.List;
import java.util.Objects;

/**
 * Immutable current-snapshot selection. An empty result means the cursor has no
 * known symbol or the symbol has no representation in this projection; it is
 * never a fallback by label. IDs follow published container/node/member/edge order.
 *
 * @param protocolVersion editor protocol version (1)
 * @param serverInstanceId authoritative server instance
 * @param diagramId validated published diagram identity
 * @param snapshotId validated semantic snapshot
 * @param rootUri diagram root URI
 * @param documentVersion exact root client version
 * @param sourceUri source-editor URI (possibly readonly)
 * @param sourceVersion exact source-editor client version
 * @param elementIds actual published presentation IDs, defensively copied
 * @param symbolIds Java-resolved opaque symbol IDs, defensively copied
 */
public record DiagramSelectionResult(int protocolVersion,String serverInstanceId,String diagramId,
        String snapshotId,String rootUri,int documentVersion,String sourceUri,int sourceVersion,
        List<String> elementIds,List<String> symbolIds) {
    public DiagramSelectionResult {
        Objects.requireNonNull(serverInstanceId); Objects.requireNonNull(diagramId);
        Objects.requireNonNull(snapshotId); Objects.requireNonNull(rootUri); Objects.requireNonNull(sourceUri);
        elementIds=List.copyOf(elementIds); symbolIds=List.copyOf(symbolIds);
    }
}
