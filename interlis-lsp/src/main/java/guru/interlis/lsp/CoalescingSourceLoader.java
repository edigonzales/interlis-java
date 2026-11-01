package guru.interlis.lsp;

import guru.interlis.execution.*;
import guru.interlis.repository.*;
import java.util.*;
import java.util.concurrent.*;

/** Shares only simultaneous immutable host reads, never prepared workspace updates or completed cache entries. */
final class CoalescingSourceLoader implements SourceLoader {
    private record Key(SourceLocator locator, FetchPolicy policy, boolean cached) { }
    private final SourceLoader delegate;
    private final Map<Key, Flight> flights=new HashMap<>();
    private CancellationReason stopped;
    private final class Flight implements CancellationToken {
        final List<CancellationToken> subscribers=new ArrayList<>();
        final CompletableFuture<LoadedSource> result=new CompletableFuture<>();
        CancellationReason abandoned;
        public boolean isCancellationRequested() {
            synchronized(flights) {
                if(abandoned==null && subscribers.stream().allMatch(CancellationToken::isCancellationRequested))
                    abandoned=subscribers.getFirst().reason();
                return abandoned!=null;
            }
        }
        public CancellationReason reason() {
            synchronized(flights) { return abandoned==null?CancellationReason.USER_REQUESTED:abandoned; }
        }
    }
    CoalescingSourceLoader(SourceLoader delegate) { this.delegate=Objects.requireNonNull(delegate); }
    public LoadedSource load(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        return read(new Key(locator,policy,false),token);
    }
    public LoadedSource loadCached(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        if(!policy.offline())throw new IllegalArgumentException("cached loading requires offline policy");
        return read(new Key(locator,policy,true),token);
    }
    private LoadedSource read(Key key, CancellationToken token) {
        Objects.requireNonNull(key.locator());Objects.requireNonNull(key.policy());Objects.requireNonNull(token);
        token.throwIfCancellationRequested();
        Flight flight;boolean first;
        synchronized(flights) {
            if(stopped!=null)throw new OperationCancelledException(stopped);
            flight=flights.get(key);
            if(flight!=null && flight.isCancellationRequested()) { flights.remove(key);flight=null; }
            first=flight==null;
            if(first) { flight=new Flight();flights.put(key,flight); }
            flight.subscribers.add(token);
        }
        if(first) {
            try {
                LoadedSource loaded=key.cached()?delegate.loadCached(key.locator(),key.policy(),flight)
                        :delegate.load(key.locator(),key.policy(),flight);
                synchronized(flights) { flights.remove(key,flight);flight.result.complete(loaded); }
            } catch(Throwable failure) {
                synchronized(flights) { flights.remove(key,flight);flight.result.completeExceptionally(failure); }
                if(failure instanceof Error error)throw error;
            }
        }
        while(true) {
            token.throwIfCancellationRequested();
            try { return flight.result.get(25,TimeUnit.MILLISECONDS); }
            catch(TimeoutException waiting) { /* independently cancellable subscriber */ }
            catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();throw new OperationCancelledException(CancellationReason.HOST_SHUTDOWN);
            } catch(ExecutionException failed) {
                if(failed.getCause() instanceof RuntimeException failure)throw failure;
                if(failed.getCause() instanceof Error failure)throw failure;
                throw new IllegalStateException(failed.getCause());
            }
        }
    }
    /** Stops joining this configuration's reads and releases waiters; active IO observes the same reason. */
    void cancelPending(CancellationReason reason) {
        synchronized(flights) {
            stopped=Objects.requireNonNull(reason);
            for(Flight flight:flights.values()) {
                flight.abandoned=reason;
                flight.result.completeExceptionally(new OperationCancelledException(reason));
            }
            flights.clear();
        }
    }
}
