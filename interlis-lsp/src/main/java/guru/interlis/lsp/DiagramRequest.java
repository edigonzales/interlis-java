package guru.interlis.lsp;

import com.google.gson.JsonObject;
import java.util.List;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

/** Versioned read-only projection request. Layout options never enter the compiler configuration. */
public record DiagramRequest(int protocolVersion,String requestId,VersionedTextDocumentIdentifier textDocument,
                             List<String> symbolIds,JsonObject options) { }
