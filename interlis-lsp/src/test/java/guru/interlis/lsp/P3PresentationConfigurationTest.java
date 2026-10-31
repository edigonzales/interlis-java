package guru.interlis.lsp;

import com.google.gson.JsonParser;
import guru.interlis.compiler.*;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class P3PresentationConfigurationTest {
    private static final String URI="memory:/presentation.ili";
    private static final String TEXT="""
            INTERLIS 2.4;
            MODEL F AT "x" VERSION "1" =
            /* first 😀
               second */
            DOMAIN Count=0..10;
            TOPIC T =
            CLASS C =
            value: Count;
            END C;
            END T;
            END F.
            """;

    @Test void aValidSmallBudgetReportsResourceCancellationAndCanBeRestored() throws Exception {
        var server=server();
        try {
            open(server,TEXT);
            server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS);
            assertDoesNotThrow(()->server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(JsonParser.parseString("""
                {"interlis":{"analysisBudget":{"maxSourceUtf16Length":10,"maxTotalSourceUtf16Length":10}}}
                """))));
            for(var request:List.<java.util.function.Supplier<CompletableFuture<?>>>of(
                    ()->server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))),
                    ()->server.getWorkspaceService().executeCommand(new ExecuteCommandParams("interlis.compile",List.of())))) {
                var failure=assertThrows(ExecutionException.class,()->request.get().get(5,TimeUnit.SECONDS));
                var response=assertInstanceOf(org.eclipse.lsp4j.jsonrpc.ResponseErrorException.class,failure.getCause());
                assertEquals(-32800,response.getResponseError().getCode()); assertTrue(response.getMessage().contains("RESOURCE_LIMIT")); assertNull(response.getResponseError().getData());
            }
            server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(JsonParser.parseString("""
                {"interlis":{"analysisBudget":{"maxSourceUtf16Length":2097152,"maxTotalSourceUtf16Length":8388608}}}
                """)));
            assertFalse(server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS).isEmpty());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void tokensFollowBoundSymbolsAndNeverSpanLineSeparatorsAndCommentsFold() throws Exception {
        var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults()));
        try {
            var initialized=server.initialize(new InitializeParams()).get(5,TimeUnit.SECONDS);
            open(server,TEXT);
            var docs=server.getTextDocumentService();
            var legend=initialized.getCapabilities().getSemanticTokensProvider().getLegend().getTokenTypes();
            var data=docs.semanticTokensFull(new SemanticTokensParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS).getData();
            String[] lines=TEXT.split("\n",-1); int line=0,column=0; boolean domainReference=false,comment=false;
            for(int i=0;i<data.size();i+=5) {
                column=data.get(i)==0?column+data.get(i+1):data.get(i+1); line+=data.get(i);
                int length=data.get(i+2); assertTrue(length>0); assertTrue(column+length<=lines[line].length());
                String spelling=lines[line].substring(column,column+length), kind=legend.get(data.get(i+3));
                if(line==7 && spelling.equals("Count")) { assertEquals("type",kind); domainReference=true; }
                if(line==2 && kind.equals("comment")) comment=true;
            }
            assertTrue(domainReference); assertTrue(comment);
            var folds=docs.foldingRange(new FoldingRangeRequestParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS);
            assertTrue(folds.stream().anyMatch(f -> f.getStartLine()==2 && f.getEndLine()==3 && "comment".equals(f.getKind())));
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void formattingUsesClientIndentAndRejectsInvalidSource() throws Exception {
        var server=server();
        try {
            open(server,TEXT);
            var docs=server.getTextDocumentService();
            var edits=docs.formatting(new DocumentFormattingParams(new TextDocumentIdentifier(URI),new FormattingOptions(4,true))).get(5,TimeUnit.SECONDS);
            assertTrue(edits.getFirst().getNewText().contains("        CLASS C"));
            docs.didChange(new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI,2),List.of(new TextDocumentContentChangeEvent("INTERLIS 2.4; MODEL"))));
            var failure=assertThrows(ExecutionException.class,() -> docs.formatting(new DocumentFormattingParams(new TextDocumentIdentifier(URI),new FormattingOptions(4,true))).get(5,TimeUnit.SECONDS));
            assertInstanceOf(org.eclipse.lsp4j.jsonrpc.ResponseErrorException.class,failure.getCause());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    @Test void configurationAppliesAtomicallyAndInvalidSettingsLeavePreviousConfiguration() throws Exception {
        var server=server();
        try {
            open(server,TEXT);
            server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(JsonParser.parseString("""
                {"interlis":{"warningPolicy":"REJECT_WARNINGS","allowedLanguages":["2.4"],
                  "resolutionPolicy":{"modelSelections":[{"modelName":"F","modelVersion":"1","sourceHint":"memory:/presentation.ili"}]},
                  "cacheLimits":{"maxSyntaxEntries":0,"maxAnalysisEntries":0,"maxQueryEntries":0,"maxRetainedSourceUtf16Units":0}}}
                """)));
            var config=server.workspaceForTest().configuration();
            assertEquals(WarningPolicy.REJECT_WARNINGS,config.compilerOptions().warningPolicy());
            assertEquals(Set.of(guru.interlis.model.LanguageVersion.ILI24),config.compilerOptions().allowedLanguages());
            assertEquals("1",config.resolutionPolicy().modelSelections().get("F").modelVersion().orElseThrow());
            assertEquals(0,config.cacheLimits().maxSyntaxEntries());
            server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(JsonParser.parseString("""
                {"interlis":{"warningPolicy":"ALLOW_WARNINGS","cacheLimits":{"maxSyntaxEntries":-1}}}
                """)));
            assertEquals(config,server.workspaceForTest().configuration());
            server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI))).get(5,TimeUnit.SECONDS);
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }

    private static InterlisLanguageServer server() { var server=new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults())); server.initialize(new InitializeParams()).join(); return server; }
    private static void open(InterlisLanguageServer server,String text) {
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(URI,"interlis",1,text)));
    }
}
