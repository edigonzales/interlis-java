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
        return create(workingDirectory,configuration,observer,new RepositoryProxyConfiguration.SystemDefault());
    }
    /** Creates an invocation-local access using an explicit immutable proxy choice. */
    public static Access create(Path workingDirectory, RepositoryCacheConfiguration configuration,
                                Consumer<RepositoryCacheEvent> observer, RepositoryProxyConfiguration proxy) {
        return createObserved(workingDirectory,configuration,CacheObservations.legacy(observer),proxy);
    }
    /** Detailed invocation-local observations contain display-only sanitized source addresses. */
    public static Access createObserved(Path workingDirectory, RepositoryCacheConfiguration configuration,
                                        Consumer<RepositoryCacheObservation> observer) {
        return createObserved(workingDirectory,configuration,observer,new RepositoryProxyConfiguration.SystemDefault());
    }
    /** Captures the proxy selector once; no global network settings are changed. */
    public static Access createObserved(Path workingDirectory, RepositoryCacheConfiguration configuration,
                                        Consumer<RepositoryCacheObservation> observer, RepositoryProxyConfiguration proxy) {
        return createObserved(workingDirectory, configuration, observer, proxy, java.time.Clock.systemUTC());
    }
    /**
     * Uses an explicit non-null wall clock for persistent download ages. Clock reads
     * do not renew entries; construction still performs no IO. All existing host
     * isolation and observer-failure conditions apply.
     */
    public static Access createObserved(Path workingDirectory, RepositoryCacheConfiguration configuration,
            Consumer<RepositoryCacheObservation> observer, RepositoryProxyConfiguration proxy, java.time.Clock clock) {
        Objects.requireNonNull(workingDirectory); Objects.requireNonNull(configuration); Objects.requireNonNull(proxy); Objects.requireNonNull(clock);
        var safe = CacheObservations.detailed(observer);
        var warned = new AtomicBoolean();
        Runnable unavailable = () -> { if(warned.compareAndSet(false,true))safe.accept(RepositoryCacheObservation.global(RepositoryCacheEvent.WRITE_UNAVAILABLE)); };
        ContentStore store=configuration.directory().<ContentStore>map(p -> new DirectoryContentStore(p,16_777_216,unavailable)).orElseGet(InMemoryContentStore::new);
        var metadata=configuration.directory().map(p -> new DirectoryMetadataStore(p,unavailable)).orElse(null);
        return new Access(new RoutingSourceLoader(new FileSourceLoader(workingDirectory),HttpSourceLoader.observed(store,metadata,safe,proxy,clock)),
                store,event -> safe.accept(RepositoryCacheObservation.global(event)),safe);
    }
}
