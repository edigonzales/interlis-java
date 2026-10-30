package guru.interlis.lsp;

import guru.interlis.compiler.*;
import guru.interlis.execution.*;
import guru.interlis.workspace.*;
import org.eclipse.lsp4j.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** INC-018/022: the owner accepts edits while a detached compiler result is delayed. */
class P3AnalysisSchedulingTest {
    private static final String URI = "memory:/scheduling.ili";
    private static final String TEXT = "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = END F.";

    @Test void detachedLanguageWorkDoesNotBlockEditsAndCannotReturnAnOldAnswer() throws Exception {
        var workspace=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults());
        var scheduler=new AnalysisScheduler(workspace,CompilerEngines.create(),CompilerServices.defaults(),ignored -> { },ignored -> { });
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var ownerCaller=Executors.newSingleThreadExecutor();
        try {
            var id=new guru.interlis.source.SourceId(URI);
            scheduler.onOwner(() -> { workspace.update(new SourceUpdate(id,new SourceExpectation.Absent(),new SourceChange.Replace(TEXT)));scheduler.schedule();return null; });
            var old=scheduler.requestNow(token -> scheduler.detached(token,() -> {
                entered.countDown();
                try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
                return CompletableFuture.completedFuture("old answer despite supersedure");
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            ownerCaller.submit(() -> scheduler.onOwner(() -> {
                var source=workspace.source(id).orElseThrow();
                workspace.update(new SourceUpdate(id,new SourceExpectation.At(source.revision()),new SourceChange.Replace(TEXT+"\n!! new")));
                scheduler.schedule(); return null;
            })).get(300,TimeUnit.MILLISECONDS);
            release.countDown();
            var failure=assertThrows(ExecutionException.class,()->old.get(5,TimeUnit.SECONDS));
            assertEquals(org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.ContentModified.getValue(),
                    assertInstanceOf(org.eclipse.lsp4j.jsonrpc.ResponseErrorException.class,failure.getCause()).getResponseError().getCode());
            assertEquals("current",scheduler.requestNow(token -> scheduler.detached(token,()->CompletableFuture.completedFuture("current"))).get(5,TimeUnit.SECONDS));
        } finally { release.countDown();ownerCaller.shutdownNow();scheduler.close();workspace.close(); }
    }

    @Test void shutdownCancelsActiveLanguageWorkAndCompletesItsRequest() throws Exception {
        var workspace=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults());
        var scheduler=new AnalysisScheduler(workspace,CompilerEngines.create(),CompilerServices.defaults(),ignored -> { },ignored -> { });
        var entered=new CountDownLatch(1); var ended=new CountDownLatch(1);
        var reason=new AtomicReference<CancellationReason>();
        try {
            var active=scheduler.requestNow(token -> scheduler.detached(token,() -> {
                entered.countDown();
                try { new CountDownLatch(1).await(5,TimeUnit.SECONDS); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                reason.set(token.reason()); assertTrue(token.isCancellationRequested()); ended.countDown();
                return CompletableFuture.completedFuture("late");
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS)); scheduler.close();
            assertTrue(active.isCancelled()); assertTrue(ended.await(5,TimeUnit.SECONDS));
            assertEquals(CancellationReason.HOST_SHUTDOWN,reason.get());
        } finally { scheduler.close();workspace.close(); }
    }

    @Test void anObsoleteCompilerCannotBlockNotificationsOrPublishAfterANewerGeneration() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var cancellations = new AtomicBoolean(); var analysisCalls = new AtomicInteger();
        CompilerEngine realEngine = CompilerEngines.create();
        Workspace realWorkspace = Workspaces.create(realEngine, WorkspaceConfiguration.defaults());
        InvocationHandler delay = (proxy, method, args) -> {
            if (method.getName().equals("analyze") && analysisCalls.incrementAndGet() == 1) {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                for (Object argument : args) if (argument instanceof CancellationToken token)
                    cancellations.set(token.isCancellationRequested());
            }
            try { return method.invoke(proxy instanceof Workspace ? realWorkspace : realEngine, args); }
            catch (InvocationTargetException failed) { throw failed.getCause(); }
        };
        Workspace delayedWorkspace = (Workspace) Proxy.newProxyInstance(Workspace.class.getClassLoader(),
                new Class<?>[]{Workspace.class}, delay);
        CompilerEngine delayedEngine = (CompilerEngine) Proxy.newProxyInstance(CompilerEngine.class.getClassLoader(),
                new Class<?>[]{CompilerEngine.class}, delay);
        var server = new InterlisLanguageServer(delayedWorkspace, delayedEngine, CompilerServices.defaults());
        server.initialize(new org.eclipse.lsp4j.InitializeParams()).join();
        var notifications = Executors.newSingleThreadExecutor();
        var publications = new LinkedBlockingQueue<PublishDiagnosticsParams>();
        server.textDocumentsForTest().sink(publications::add);
        try {
            var open = notifications.submit(() -> server.getTextDocumentService().didOpen(
                    new DidOpenTextDocumentParams(new TextDocumentItem(URI, "interlis", 1, TEXT))));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            open.get(300, TimeUnit.MILLISECONDS);
            var edit = notifications.submit(() -> server.getTextDocumentService().didChange(
                    new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(URI, 2),
                            List.of(new TextDocumentContentChangeEvent(TEXT + "\n!! newest")))));
            edit.get(300, TimeUnit.MILLISECONDS);
            var current = publications.poll(5, TimeUnit.SECONDS);
            assertNotNull(current); assertEquals(2, current.getVersion());
            release.countDown();
            // An explicit query provides an owner-queue barrier after the latest result.
            server.getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(URI)))
                    .get(5, TimeUnit.SECONDS);
            assertTrue(cancellations.get());
            assertTrue(publications.stream().allMatch(value -> value.getVersion() == 2));
        } finally {
            release.countDown(); notifications.shutdownNow(); server.shutdown().get(5, TimeUnit.SECONDS);
        }
    }
}
