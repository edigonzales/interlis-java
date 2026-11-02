package guru.interlis.lsp;

import org.eclipse.lsp4j.*;

/** An actual Enter gesture against the exact new native document version. */
public record EditorAssistParams(
        int protocolVersion,
        VersionedTextDocumentIdentifier textDocument,
        Position position,
        FormattingOptions options) {}
