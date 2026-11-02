package guru.interlis.lsp;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
/** Current requires an exact open document version. Workspace is an explicit separate mode. */
public record CompileModelParams(String mode,VersionedTextDocumentIdentifier textDocument,String modelName,String modelVersion) { }
