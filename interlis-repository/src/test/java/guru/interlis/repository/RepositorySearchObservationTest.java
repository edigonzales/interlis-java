package guru.interlis.repository;

import guru.interlis.execution.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.repository.RepositoryClosureContractTest.*;

class RepositorySearchObservationTest {
    private static final String BASE = "https://example.org/repository/";
    private final Map<String, String> contents = new ConcurrentHashMap<>(Map.of(
            BASE + "ilimodels.xml", index(entry("Dep", "1", "dep.ili")),
            BASE + "dep.ili", model("Dep", "1", "")));
    private RepositoryClosureService service(Consumer<String> loads) {
        SourceLoader loader = (locator, policy, token) -> {
            token.throwIfCancellationRequested(); loads.accept(locator.location());
            var text = contents.get(locator.location());
            if (text == null) throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND, "missing", null);
            return loaded(locator.location(), text);
        };
        return new RepositoryClosureService(loader, new InMemoryContentStore());
    }

    @Test void synchronousObservationsBracketActualLoadsWithoutChangingStoredProvenance() {
        var order = new ArrayList<String>();
        var service = service(uri -> order.add("LOAD " + uri));
        try (var w = workspace(model("Root", "1", "IMPORTS Dep;"))) {
            var revision = w.currentRevision();
            var update = prepared(service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(BASE)),
                    current -> order.add(current.kind().name()), TOKEN));
            assertEquals(List.of("LOOKUP", "LOAD " + BASE + "ilimodels.xml", "SELECTED", "LOAD " + BASE + "dep.ili"), order);
            assertEquals(List.of(RepositorySearchStep.Kind.LOOKUP, RepositorySearchStep.Kind.SELECTED),
                    update.lock().steps().stream().map(RepositorySearchStep::kind).toList());
            assertEquals(revision, w.currentRevision());
        }
    }

    @Test void throwingObserverDoesNotChangeResult() {
        var service = service(uri -> { });
        ResolutionLock lock;
        try (var w = workspace(model("Root", "1", "IMPORTS Dep;"))) {
            lock = prepared(service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(BASE)), TOKEN)).lock();
        }
        var calls = new AtomicInteger();
        try (var w = workspace(model("Root", "1", "IMPORTS Dep;"))) {
            assertEquals(lock, prepared(service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(BASE)),
                    current -> { calls.incrementAndGet(); throw new IllegalStateException("observer"); }, TOKEN)).lock());
        }
        assertEquals(2, calls.get());
    }

    @Test void cancellationFromObserverRemainsCancellationAndNeverReturnsPartialPreparation() {
        var cancelled = new AtomicBoolean(); var events = new ArrayList<String>();
        var loads = new ArrayList<String>(); var service = service(loads::add);
        try (var w = workspace(model("Root", "1", "IMPORTS Dep;"))) {
            var revision = w.currentRevision();
            var result = service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(BASE)), current -> {
                events.add(current.kind().name()); cancelled.set(true);
            }, cancelled::get);
            assertInstanceOf(RepositoryResolutionOutcome.Cancelled.class, result);
            assertEquals(revision, w.currentRevision());
            assertEquals(List.of("LOOKUP"), events); assertTrue(loads.isEmpty());
        }
    }

    @Test void completeLocalClosureDoesNotPublishSearchStepsAndRejectsNullObserver() {
        var loads = new ArrayList<String>(); var service = service(loads::add);
        try (var w = workspace(model("Root", "1", ""))) {
            var events = new ArrayList<RepositorySearchStep>();
            prepared(service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(BASE)), events::add, TOKEN));
            assertTrue(events.isEmpty()); assertTrue(loads.isEmpty());
            assertThrows(NullPointerException.class, () -> service.resolve(w, request(w),
                    RepositoryResolveOptions.defaults(config(BASE)), null, TOKEN));
        }
    }

    @Test void parallelInvocationsOnOneServiceKeepTheirObserversSeparate() throws Exception {
        var barrier = new CyclicBarrier(2);
        var service = service(uri -> {
            if (uri.endsWith("dep.ili")) try { barrier.await(5, TimeUnit.SECONDS); }
            catch (Exception failure) { throw new AssertionError(failure); }
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<Future<List<String>>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                var events = new ArrayList<String>();
                try (var w = workspace(model("Root", "1", "IMPORTS Dep;"))) {
                    prepared(service.resolve(w, request(w), RepositoryResolveOptions.defaults(config(BASE)),
                            current -> events.add(current.kind().name()), TOKEN));
                }
                return events;
            }));
            for (var task : tasks) assertEquals(List.of("LOOKUP", "SELECTED"), task.get(10, TimeUnit.SECONDS));
        }
    }
}
