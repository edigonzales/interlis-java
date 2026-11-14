package guru.interlis.lsp;

import guru.interlis.execution.*;
import guru.interlis.repository.*;
import guru.interlis.workspace.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded host IO over immutable captures. Only the caller's owner queue may apply returned batches. */
final class RepositoryHostCoordinator implements AutoCloseable {
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2),r -> { var t=new Thread(r,"interlis-lsp-repositories");t.setDaemon(true);return t; });
    private final AtomicBoolean closed=new AtomicBoolean();
    private final Consumer<String> operations;
    private final java.util.Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();
    private RepositorySettings settings=RepositorySettings.defaults();
    private HostRepositories.Access access;
    private CoalescingSourceLoader loader;
    RepositoryHostCoordinator(Consumer<String> operations) { this.operations=operations; reconfigure(settings); }
    void reconfigure(RepositorySettings value) {
        // A repeated client settings notification must not cancel the current capture.
        // Diagnostic language does not change repository IO authority.
        if (loader != null && value.options().equals(settings.options()) && value.cache().equals(settings.cache())) {
            settings = value;
            return;
        }
        if(loader!=null)loader.cancelPending(CancellationReason.SUPERSEDED);
        settings=value;
        access=HostRepositories.create(Path.of(System.getProperty("user.dir")),value.cache(),event -> {
            if(event==RepositoryCacheEvent.WRITE_UNAVAILABLE) operations.accept("Repository cache is unavailable; downloaded sources remain usable for this session.");
        });
        loader=new CoalescingSourceLoader(access.loader());
    }
    boolean needsResolution(WorkspaceCapture capture) {
        return !capture.resolution().complete() || verifiesLoadedSources();
    }
    boolean verifiesLoadedSources() {
        var options = settings.options();
        return !options.modelPins().isEmpty() || !options.sourcePins().isEmpty() || options.lock().isPresent();
    }
    CompletableFuture<RepositoryResolutionOutcome> resolve(Workspace owner,WorkspaceCapture capture,
            WorkspaceConfiguration configuration,CancellationToken token) {
        var result=new CompletableFuture<RepositoryResolutionOutcome>();
        pending.add(result);result.whenComplete((value,failure) -> pending.remove(result));
        var service=new RepositoryClosureService(loader,access.contentStore(),access.observer());var options=settings.options();
        CancellationToken bounded=new CancellationToken() {
            public boolean isCancellationRequested() { return closed.get() || result.isCancelled() || token.isCancellationRequested(); }
            public CancellationReason reason() { return closed.get()?CancellationReason.HOST_SHUTDOWN:token.reason(); }
        };
        try { io.execute(() -> {
            try { result.complete(service.resolveCaptured(owner,capture,configuration,options,bounded)); }
            catch(RuntimeException failure) { result.completeExceptionally(failure); }
        }); } catch(RejectedExecutionException full) { result.completeExceptionally(LspFailures.error(
                org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode.RequestFailed,"repository queue is full or closed")); }
        return result;
    }
    @Override public void close() { closed.set(true);loader.cancelPending(CancellationReason.HOST_SHUTDOWN);pending.forEach(f -> f.cancel(false));pending.clear();io.shutdownNow(); }
}
