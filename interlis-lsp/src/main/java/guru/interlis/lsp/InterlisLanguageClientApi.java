package guru.interlis.lsp;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
/** Optional read-only client notifications, using the same stdio transport as standard LSP. */
public interface InterlisLanguageClientApi extends LanguageClient {
    @JsonNotification("interlis/analysisReady") void analysisReady(AnalysisReady ready);
}
