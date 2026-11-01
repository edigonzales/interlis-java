package guru.interlis.lsp;

import guru.interlis.execution.*;
import guru.interlis.repository.*;
import guru.interlis.source.SourceId;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoalescingSourceLoaderTest {
    private static final SourceLocator URI=new SourceLocator("https://example.invalid/Dep.ili");
    private static final FetchPolicy POLICY=FetchPolicy.defaults();
    private static LoadedSource source(String text) { return new LoadedSource(new SourceId(URI.location()),text,"digest",URI.location()); }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5,TimeUnit.SECONDS),"timed out waiting for IO coordination"); }
        catch(InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
    }
    private static final class Subscriber implements CancellationToken {
        final AtomicBoolean cancelled=new AtomicBoolean();
        final AtomicInteger checks=new AtomicInteger();
        final CountDownLatch joined=new CountDownLatch(1);
        public boolean isCancellationRequested() {
            if(checks.incrementAndGet()>=2)joined.countDown();
            return cancelled.get();
        }
    }
    @Test void simultaneousReadIsSharedAndOneCancellationDoesNotCancelTheOther() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        var loader=new CoalescingSourceLoader((uri,policy,token) -> {
            calls.incrementAndGet();entered.countDown();await(release);token.throwIfCancellationRequested();return source("first");
        });
        var firstToken=new Subscriber();var secondToken=new Subscriber();
        try(var threads=Executors.newFixedThreadPool(2)) {
            var first=threads.submit(() -> loader.load(URI,POLICY,firstToken));await(entered);
            var second=threads.submit(() -> loader.load(URI,POLICY,secondToken));await(secondToken.joined);
            firstToken.cancelled.set(true);release.countDown();
            assertInstanceOf(OperationCancelledException.class,assertThrows(ExecutionException.class,()->first.get(5,TimeUnit.SECONDS)).getCause());
            assertEquals("first",second.get(5,TimeUnit.SECONDS).text());assertEquals(1,calls.get());
        } finally { release.countDown(); }
    }
    @Test void completedReadsAreNotCachedAndErrorsDoNotPoisonTheNextRead() {
        var calls=new AtomicInteger();var loader=new CoalescingSourceLoader((uri,policy,token) -> {
            int number=calls.incrementAndGet();if(number==1)throw new IllegalStateException("IO failed");return source("version"+number);
        });
        assertThrows(IllegalStateException.class,()->loader.load(URI,POLICY,CancellationToken.none()));
        assertEquals("version2",loader.load(URI,POLICY,CancellationToken.none()).text());
        assertEquals("version3",loader.load(URI,POLICY,CancellationToken.none()).text());
    }
    @Test void abandonedReadCannotBeResurrectedByANewSubscriber() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        var oldToken=new Subscriber();var loader=new CoalescingSourceLoader((uri,policy,token) -> {
            if(calls.incrementAndGet()==1) { entered.countDown();await(release);token.throwIfCancellationRequested(); }
            return source("fresh");
        });
        try(var threads=Executors.newSingleThreadExecutor()) {
            var old=threads.submit(()->loader.load(URI,POLICY,oldToken));await(entered);oldToken.cancelled.set(true);
            assertEquals("fresh",loader.load(URI,POLICY,CancellationToken.none()).text());release.countDown();
            assertInstanceOf(OperationCancelledException.class,assertThrows(ExecutionException.class,()->old.get(5,TimeUnit.SECONDS)).getCause());
            assertEquals(2,calls.get());
        } finally { release.countDown(); }
    }
    @Test void configurationReplacementReleasesWaitersAndCancelsActiveIo() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var loader=new CoalescingSourceLoader((uri,policy,token)-> { entered.countDown();await(release);token.throwIfCancellationRequested();return source("old"); });
        var waiting=new Subscriber();
        try(var threads=Executors.newFixedThreadPool(2)) {
            var first=threads.submit(()->loader.load(URI,POLICY,CancellationToken.none()));await(entered);
            var second=threads.submit(()->loader.load(URI,POLICY,waiting));await(waiting.joined);
            loader.cancelPending(CancellationReason.SUPERSEDED);
            var failure=assertThrows(ExecutionException.class,()->second.get(5,TimeUnit.SECONDS));
            assertEquals(CancellationReason.SUPERSEDED,((OperationCancelledException)failure.getCause()).reason());
            release.countDown();assertThrows(ExecutionException.class,()->first.get(5,TimeUnit.SECONDS));
            assertThrows(OperationCancelledException.class,()->loader.load(URI,POLICY,CancellationToken.none()));
        } finally { release.countDown(); }
    }
    @Test void offlineReadsUseOnlyTheExplicitCachePort() {
        var online=new AtomicInteger();var cached=new AtomicInteger();
        var loader=new CoalescingSourceLoader(new SourceLoader() {
            public LoadedSource load(SourceLocator uri,FetchPolicy policy,CancellationToken token) { online.incrementAndGet();throw new AssertionError("network"); }
            public LoadedSource loadCached(SourceLocator uri,FetchPolicy policy,CancellationToken token) { cached.incrementAndGet();return source("offline"); }
        });
        assertEquals("offline",loader.loadCached(URI,new FetchPolicy(true,1024,Duration.ofSeconds(1)),CancellationToken.none()).text());
        assertThrows(IllegalArgumentException.class,()->loader.loadCached(URI,POLICY,CancellationToken.none()));
        assertEquals(0,online.get());assertEquals(1,cached.get());
    }
}
