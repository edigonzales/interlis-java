package guru.interlis.lsp;

import java.util.concurrent.*;
import java.util.function.*;

/** Mapping preserves cancellation toward the actual host/compiler operation. */
final class LspFutures {
    private LspFutures() { }
    static <T,R> CompletableFuture<R> map(CompletableFuture<T> upstream,Function<T,R> mapper,Consumer<String> operations) {
        var result=new CompletableFuture<R>() {
            @Override public boolean cancel(boolean interrupt) {
                boolean changed=super.cancel(interrupt); if(changed) upstream.cancel(interrupt); return changed;
            }
        };
        upstream.whenComplete((value,failure) -> {
            if(failure instanceof CancellationException) result.cancel(false);
            else if(failure!=null) result.completeExceptionally(LspFailures.response(failure,operations));
            else try { result.complete(mapper.apply(value)); }
            catch(RuntimeException unexpected) { result.completeExceptionally(LspFailures.internal(unexpected,operations)); }
        });
        return result;
    }
    static <T,R> CompletableFuture<R> compose(CompletableFuture<T> upstream,
            Function<T,CompletableFuture<R>> mapper,Consumer<String> operations) {
        var next=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<R>>();
        var result=new CompletableFuture<R>() {
            @Override public boolean cancel(boolean interrupt) {
                boolean changed=super.cancel(interrupt);
                if(changed) { upstream.cancel(interrupt); var active=next.get();if(active!=null) active.cancel(interrupt); }
                return changed;
            }
        };
        upstream.whenComplete((value,failure) -> {
            if(result.isDone()) return;
            if(failure!=null) { result.completeExceptionally(LspFailures.response(failure,operations));return; }
            try {
                var active=mapper.apply(value);next.set(active);
                if(result.isCancelled()) { active.cancel(false);return; }
                active.whenComplete((answer,problem) -> {
                    if(problem!=null) result.completeExceptionally(LspFailures.response(problem,operations));else result.complete(answer);
                });
            } catch(RuntimeException problem) { result.completeExceptionally(LspFailures.response(problem,operations)); }
        });
        return result;
    }

}
