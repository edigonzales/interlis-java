package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.Test;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end LSP JSON-RPC cancellation through the standard $/cancelRequest. */
class LspProtocolCancellationTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL CancelModel AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Building =
                  height : 0..999;
                END Building;
              END Data;
            END CancelModel.
            """;

    @Test
    void cancelNotificationProducesRequestCancelledAndSessionSurvives() throws Exception {
        Workspace real = Workspaces.create(CompilerEngines.create(),
                WorkspaceConfiguration.defaults());
        CountDownLatch compileEntered = new CountDownLatch(1);
        CountDownLatch cancellationObserved = new CountDownLatch(1);
        Workspace slow = (Workspace) Proxy.newProxyInstance(
                Workspace.class.getClassLoader(), new Class<?>[]{Workspace.class},
                (proxy, method, args) -> {
                    if ("compile".equals(method.getName())) {
                        compileEntered.countDown();
                        for (int i = 0; i < 400; i++) {
                            if (args != null && args.length > 1
                                    && args[1] instanceof CancellationToken token
                                    && token.isCancellationRequested()) {
                                cancellationObserved.countDown();
                                throw new OperationCancelledException(
                                        CancellationReason.USER_REQUESTED);
                            }
                            Thread.sleep(5);
                        }
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        InterlisLanguageServer server = new InterlisLanguageServer(slow);
        PipedOutputStream clientOut = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream(clientOut);
        PipedOutputStream serverOut = new PipedOutputStream();
        PipedInputStream clientIn = new PipedInputStream(serverOut);
        var serverLauncher = LSPLauncher.createServerLauncher(server, serverIn, serverOut);
        server.connect(serverLauncher.getRemoteProxy());
        serverLauncher.startListening();
        var clientLauncher = LSPLauncher.createClientLauncher(new Client(), clientIn, clientOut);
        clientLauncher.startListening();
        var endpoint = clientLauncher.getRemoteEndpoint();
        try {
            endpoint.request("initialize", new InitializeParams()).get(5, TimeUnit.SECONDS);
            endpoint.notify("initialized", new InitializedParams());
            String uri = "memory:/cancel.ili";
            endpoint.notify("textDocument/didOpen", new DidOpenTextDocumentParams(
                    new TextDocumentItem(uri, "interlis", 1, MODEL)));
            Thread.sleep(100);

            var compile = endpoint.request("workspace/executeCommand",
                    new ExecuteCommandParams("interlis.compile", List.of()));
            assertTrue(compileEntered.await(2, TimeUnit.SECONDS),
                    "compile request did not reach the workspace");
            compile.cancel(true);
            assertTrue(cancellationObserved.await(2, TimeUnit.SECONDS),
                    "compiler did not observe cancellation");

            Object diagram = endpoint.request("workspace/executeCommand",
                    new ExecuteCommandParams("interlis.diagram",
                            List.<Object>of(Map.of("uri", uri))))
                    .get(5, TimeUnit.SECONDS);
            assertTrue(String.valueOf(diagram).contains("Building"),
                    "session did not survive cancellation");
            endpoint.request("shutdown", null).get(5, TimeUnit.SECONDS);
            endpoint.notify("exit", null);
        } finally {
            try {
                clientOut.close();
                serverOut.close();
            } catch (java.io.IOException ignored) {
                // Best-effort cleanup.
            }
        }
    }

    private static final class Client implements LanguageClient {
        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(
                ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }
}
