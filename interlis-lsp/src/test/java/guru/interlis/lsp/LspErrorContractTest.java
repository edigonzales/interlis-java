package guru.interlis.lsp;

import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerEngine;
import guru.interlis.compiler.CompilerServices;
import guru.interlis.workspace.Workspace;
import guru.interlis.workspace.WorkspaceConfiguration;
import guru.interlis.workspace.Workspaces;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
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
    @Test void compilerFailureIdentitySurvivesTheRpcBoundary() throws Exception {
        var id=new guru.interlis.execution.FailureId("failure-p3-review-control");
        var code=new guru.interlis.diagnostics.DiagnosticCode("ILI-INF-0001");
        var diagnostic=new guru.interlis.diagnostics.Diagnostic(guru.interlis.diagnostics.DiagnosticIds.create(code,guru.interlis.diagnostics.Phase.INFRASTRUCTURE,java.util.Optional.empty(),"diagnostic.internal",List.of()),
                code,guru.interlis.diagnostics.Severity.ERROR,guru.interlis.diagnostics.Phase.INFRASTRUCTURE,"diagnostic.internal",List.of(),java.util.Optional.empty(),List.of(),java.util.Optional.empty(),List.of(),java.util.Optional.empty(),java.util.Optional.of(id),List.of());
        var engine=CompilerEngines.create();
        var failing=(CompilerEngine)Proxy.newProxyInstance(CompilerEngine.class.getClassLoader(),new Class<?>[]{CompilerEngine.class},(proxy,method,args)->{
            if(method.getName().equals("compile")) throw new guru.interlis.compiler.CompilerInternalException(id,diagnostic,new IllegalStateException("PRIVATE_FAILURE_DETAIL"));
            try { return method.invoke(engine,args); } catch(InvocationTargetException failure) { throw failure.getCause(); }
        });
        try(var harness=new Harness(Workspaces.create(engine,WorkspaceConfiguration.defaults()),failing)) {
            harness.open("INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = END F.");
            var failure=assertThrows(ExecutionException.class,()->harness.compileAsync().get(5,TimeUnit.SECONDS));
            var response=(ResponseErrorException)failure.getCause();
            assertEquals(-32603,response.getResponseError().getCode()); assertTrue(response.getMessage().contains(id.value()));
            org.junit.jupiter.api.Assertions.assertNull(response.getResponseError().getData()); assertTrue(!response.getMessage().contains("PRIVATE_FAILURE_DETAIL"));
        }
    }
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
            assertEquals("rejected", harness.compile().outcome());
        }

        CompilerEngine realEngine = CompilerEngines.create();
        Workspace real = Workspaces.create(realEngine, WorkspaceConfiguration.defaults());
        CompilerEngine failing = (CompilerEngine) Proxy.newProxyInstance(
                CompilerEngine.class.getClassLoader(), new Class<?>[]{CompilerEngine.class},
                (proxy, method, args) -> {
                    if ("compile".equals(method.getName())) {
                        throw new IllegalStateException("injected internal failure");
                    }
                    try {
                        return method.invoke(realEngine, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
        try (Harness harness = new Harness(real, failing)) {
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
            org.junit.jupiter.api.Assertions.assertNull(((ResponseErrorException) failure.getCause()).getResponseError().getData());
            assertTrue(failure.getCause().getMessage().contains("lsp-"));
        }
    }

    private static final class Harness implements AutoCloseable {
        private final PipedOutputStream clientOut;
        private final PipedOutputStream serverOut;
        private final Launcher<InterlisLanguageServerApi> clientLauncher;
        private final Launcher<LanguageClient> serverLauncher;
        private final RemoteEndpoint endpoint;

        Harness(Workspace workspace) throws Exception {
            this(workspace, CompilerEngines.create());
        }

        Harness(Workspace workspace, CompilerEngine engine) throws Exception {
            clientOut = new PipedOutputStream();
            var serverIn = new PipedInputStream(clientOut);
            serverOut = new PipedOutputStream();
            var clientIn = new PipedInputStream(serverOut);
            var server = new InterlisLanguageServer(workspace, engine, CompilerServices.defaults());
            serverLauncher = LSPLauncher.createServerLauncher(server, serverIn, serverOut);
            server.connect(serverLauncher.getRemoteProxy());
            serverLauncher.startListening();
            clientLauncher = new LSPLauncher.Builder<InterlisLanguageServerApi>().setLocalService(new Client())
                    .setRemoteInterface(InterlisLanguageServerApi.class).setInput(clientIn).setOutput(clientOut).create();
            clientLauncher.startListening();
            endpoint = clientLauncher.getRemoteEndpoint();
            endpoint.request("initialize", new InitializeParams()).get(5, TimeUnit.SECONDS);
            endpoint.notify("initialized", new InitializedParams());
        }

        void open(String model) throws Exception {
            endpoint.notify("textDocument/didOpen", new DidOpenTextDocumentParams(
                    new TextDocumentItem("memory:/error.ct.ili", "interlis", 1, model)));
            endpoint.request("textDocument/documentSymbol", new org.eclipse.lsp4j.DocumentSymbolParams(
                    new org.eclipse.lsp4j.TextDocumentIdentifier("memory:/error.ct.ili"))).get(5, TimeUnit.SECONDS);
        }

        CompileReport compile() throws Exception {
            return compileAsync().get(5, TimeUnit.SECONDS);
        }

        CompletableFuture<CompileReport> compileAsync() {
            return clientLauncher.getRemoteProxy().compileModel(new CompileModelParams("workspace", null, null, null));
        }

        int errorCode(String method, Object params) throws Exception {
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> endpoint.request(method, params).get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ResponseErrorException,
                    String.valueOf(failure.getCause()));
            org.junit.jupiter.api.Assertions.assertNull(((ResponseErrorException)failure.getCause()).getResponseError().getData());
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
