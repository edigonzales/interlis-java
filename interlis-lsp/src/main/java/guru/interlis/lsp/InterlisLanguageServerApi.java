package guru.interlis.lsp;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import java.util.concurrent.CompletableFuture;
/** Versioned editor requests exported by the real stdio launcher. Standard language queries remain standard LSP. */
public interface InterlisLanguageServerApi extends LanguageServer {
    @JsonRequest("interlis/compile") CompletableFuture<CompileReport> compileModel(CompileModelParams params);
    @JsonRequest("interlis/source") CompletableFuture<SourceContent> sourceContent(SourceContentParams params);
}
