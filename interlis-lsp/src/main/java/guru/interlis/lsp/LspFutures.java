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
}
