package guru.interlis.repository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Explicit JVM composition; create one access per invocation, with no global cache or run state. */
public final class HostRepositories {
    private HostRepositories() { }
    /** Loader/store share one cache. Constructors alone perform no filesystem IO. */
    public record Access(SourceLoader loader, ContentStore contentStore, Consumer<RepositoryCacheEvent> observer,
                         Consumer<RepositoryCacheObservation> observations) {
        public Access(SourceLoader loader,ContentStore store,Consumer<RepositoryCacheEvent> observer) {
            this(loader,store,observer,CacheObservations.legacy(observer));
        }
        public Access { Objects.requireNonNull(loader); Objects.requireNonNull(contentStore); Objects.requireNonNull(observer); observations=CacheObservations.detailed(observations); }
        public RepositoryClosureService closureService() { return RepositoryClosureService.observed(loader,contentStore,observations); }
    }
    /**
     * Builds host adapters. Observations contain no URLs, headers or paths; callback
     * failures never change loading. Persistent write failures are reported once per access.
     */
    public static Access create(Path workingDirectory, RepositoryCacheConfiguration configuration,
                                Consumer<RepositoryCacheEvent> observer) {
        return createObserved(workingDirectory,configuration,CacheObservations.legacy(observer));
    }
    /** Detailed invocation-local observations contain display-only sanitized source addresses. */
    public static Access createObserved(Path workingDirectory, RepositoryCacheConfiguration configuration,
                                        Consumer<RepositoryCacheObservation> observer) {
        Objects.requireNonNull(workingDirectory); Objects.requireNonNull(configuration);
        var safe = CacheObservations.detailed(observer);
        var warned = new AtomicBoolean();
        Runnable unavailable = () -> { if(warned.compareAndSet(false,true))safe.accept(RepositoryCacheObservation.global(RepositoryCacheEvent.WRITE_UNAVAILABLE)); };
        ContentStore store=configuration.directory().<ContentStore>map(p -> new DirectoryContentStore(p,16_777_216,unavailable)).orElseGet(InMemoryContentStore::new);
        var metadata=configuration.directory().map(p -> new DirectoryMetadataStore(p,unavailable)).orElse(null);
        return new Access(new RoutingSourceLoader(new FileSourceLoader(workingDirectory),HttpSourceLoader.observed(store,metadata,safe)),
                store,event -> safe.accept(RepositoryCacheObservation.global(event)),safe);
    }
}
