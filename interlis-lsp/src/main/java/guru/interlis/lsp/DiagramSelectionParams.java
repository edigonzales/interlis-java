package guru.interlis.lsp;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

/**
 * Read-only source-selection intent bound to one published diagram and two exact
 * client versions. The source may be the root, another open source, or a current
 * readonly import handle. Positions use original-text UTF-16 coordinates.
 *
 * @param protocolVersion editor protocol version (1)
 * @param serverInstanceId expected server instance
 * @param diagramId published diagram identity
 * @param snapshotId expected semantic snapshot
 * @param textDocument exact diagram root version
 * @param sourceDocument exact source-editor version
 * @param position source cursor position, not a label or an inferred name
 */
public record DiagramSelectionParams(int protocolVersion, String serverInstanceId,
        String diagramId, String snapshotId, VersionedTextDocumentIdentifier textDocument,
        VersionedTextDocumentIdentifier sourceDocument, Position position) { }
