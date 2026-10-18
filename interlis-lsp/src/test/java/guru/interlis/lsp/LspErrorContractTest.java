package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** LSP-016: protocol, language and internal failures produce different answers. */
class LspErrorContractTest {
    @Test
    void protocolLanguageAndInternalFailuresAreDistinct() throws Exception {
        try (Harness harness = new Harness(Workspaces.create(
                CompilerEngines.create(), WorkspaceConfiguration.defaults()))) {
            assertEquals(-32601, harness.errorCode("unknown/method", Map.of()));
        }

        try (Harness harness = new Harness(Workspaces.create(
                CompilerEngines.create(), WorkspaceConfiguration.defaults()))) {
            harness.open("""
                    INTERLIS 2.4;
                    MODEL Broken AT "https://example.interlis.ch" VERSION "1.0" =
                      TOPIC Data =
                        CLASS BrokenClass =
                          label : ;
                        END BrokenClass;
                      END Data;
                    END Broken.
                    """);
            Object result = harness.compile();
            assertTrue(String.valueOf(result).contains("rejected"), String.valueOf(result));
        }

        Workspace real = Workspaces.create(CompilerEngines.create(),
                WorkspaceConfiguration.defaults());
        Workspace failing = (Workspace) Proxy.newProxyInstance(
                Workspace.class.getClassLoader(), new Class<?>[]{Workspace.class},
                (proxy, method, args) -> {
                    if ("compile".equals(method.getName())) {
                        throw new IllegalStateException("injected internal failure");
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        try (Harness harness = new Harness(failing)) {
            harness.open("""
                    INTERLIS 2.4;
                    MODEL Error AT "https://example.interlis.ch" VERSION "1.0" =
                      TOPIC Data =
                        CLASS Item =
                          value : 0..9;
                        END Item;
                      END Data;
                    END Error.
                    """);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> harness.compileAsync().get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ResponseErrorException);
            assertEquals(-32603,
                    ((ResponseErrorException) failure.getCause()).getResponseError().getCode());
        }
    }

    private static final class Harness implements AutoCloseable {
        private final PipedOutputStream clientOut;
        private final PipedOutputStream serverOut;
        private final Launcher<LanguageServer> clientLauncher;
        private final Launcher<LanguageClient> serverLauncher;
        private final RemoteEndpoint endpoint;

        Harness(Workspace workspace) throws Exception {
            clientOut = new PipedOutputStream();
            var serverIn = new PipedInputStream(clientOut);
            serverOut = new PipedOutputStream();
            var clientIn = new PipedInputStream(serverOut);
            var server = new InterlisLanguageServer(workspace);
            serverLauncher = LSPLauncher.createServerLauncher(server, serverIn, serverOut);
            server.connect(serverLauncher.getRemoteProxy());
            serverLauncher.startListening();
            clientLauncher = LSPLauncher.createClientLauncher(new Client(), clientIn, clientOut);
            clientLauncher.startListening();
            endpoint = clientLauncher.getRemoteEndpoint();
            endpoint.request("initialize", new InitializeParams()).get(5, TimeUnit.SECONDS);
            endpoint.notify("initialized", new InitializedParams());
        }

        void open(String model) throws Exception {
            endpoint.notify("textDocument/didOpen", new DidOpenTextDocumentParams(
                    new TextDocumentItem("memory:/error.ct.ili", "interlis", 1, model)));
            Thread.sleep(100);
        }

        Object compile() throws Exception {
            return compileAsync().get(5, TimeUnit.SECONDS);
        }

        CompletableFuture<Object> compileAsync() {
            return endpoint.request("workspace/executeCommand",
                    new ExecuteCommandParams("interlis.compile", List.of()));
        }

        int errorCode(String method, Object params) throws Exception {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> endpoint.request(method, params).get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ResponseErrorException,
                    String.valueOf(failure.getCause()));
            return ((ResponseErrorException) failure.getCause()).getResponseError().getCode();
        }

        @Override
        public void close() {
            try {
                endpoint.request("shutdown", null).get(5, TimeUnit.SECONDS);
                endpoint.notify("exit", null);
            } catch (Exception ignored) {
                // Best-effort cleanup after a deliberately failing test.
            } finally {
                try {
                    clientOut.close();
                    serverOut.close();
                } catch (java.io.IOException ignored) {
                    // Pipes are already closed on the failure path.
                }
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
