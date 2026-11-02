package guru.interlis.lsp;

import com.google.gson.JsonObject;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

/** Exact root/version and pure export presentation choices. */
public record ExportArtifactParams(
        int protocolVersion,
        VersionedTextDocumentIdentifier textDocument,
        String format,
        String modelName,
        String modelVersion,
        JsonObject options) {}
