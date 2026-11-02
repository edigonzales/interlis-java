package guru.interlis.lsp;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
/** A navigation intent names a published element and its complete source/snapshot/session identity. */
public record DiagramNavigationParams(int protocolVersion,String serverInstanceId,String diagramId,String snapshotId,
                                     VersionedTextDocumentIdentifier textDocument,String elementId) { }
