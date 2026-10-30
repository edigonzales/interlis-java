package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class P3CompletionProtocolTest {
    @Test void completionListPreservesTruncationInsertTextAndTheWholeReplacementRange() throws Exception {
        var text = new StringBuilder("INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = DOMAIN ");
        for (int i=0;i<105;i++) text.append(String.format(java.util.Locale.ROOT,"W%03d = TEXT; ",i));
        text.append("TOPIC T = CLASS C = value: W000; END C; END T; END F.");
        String uri = "memory:/completion-protocol.ili", model = text.toString();
        int start = model.lastIndexOf("W000");
        var server = new InterlisLanguageServer(Workspaces.create(CompilerEngines.create(), WorkspaceConfiguration.defaults()));
        server.initialize(new org.eclipse.lsp4j.InitializeParams()).join();
        try {
            server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri,"interlis",1,model)));
            var result = server.getTextDocumentService().completion(new CompletionParams(new TextDocumentIdentifier(uri),
                    new Position(0,start+1))).get(5,TimeUnit.SECONDS);
            assertTrue(result.isRight());
            assertTrue(result.getRight().isIncomplete()); assertEquals(100,result.getRight().getItems().size());
            var item = result.getRight().getItems().getFirst();
            assertEquals("W000",item.getInsertText());
            assertEquals(new Range(new Position(0,start),new Position(0,start+4)),item.getTextEdit().getLeft().getRange());
            assertEquals("W000",item.getTextEdit().getLeft().getNewText());
        } finally { server.shutdown().get(5,TimeUnit.SECONDS); }
    }
}
