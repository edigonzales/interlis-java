package guru.interlis.repository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Explicit JVM composition; create one access per invocation, with no global cache or run state. */
public final class HostRepositories {
    private HostRepositories() { }
    /** Loader/store share one cache. Constructors alone perform no filesystem IO. */
    public record Access(SourceLoader loader, ContentStore contentStore,
                         Consumer<RepositoryCacheObservation> observations) {
        public Access {
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(contentStore, "contentStore");
            Objects.requireNonNull(observations, "observations");
        }
        public RepositoryClosureService closureService() {
            return new RepositoryClosureService(loader, contentStore, observations);
        }
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
        Objects.requireNonNull(observer, "observer");
        var warned = new AtomicBoolean();
        Runnable unavailable = () -> { if(warned.compareAndSet(false,true))RepositoryCacheObservation.global(RepositoryCacheEvent.WRITE_UNAVAILABLE).publishTo(observer); };
        ContentStore store=configuration.directory().<ContentStore>map(p -> new DirectoryContentStore(p,16_777_216,unavailable)).orElseGet(InMemoryContentStore::new);
        var metadata=configuration.directory().map(p -> new DirectoryMetadataStore(p,unavailable)).orElse(null);
        return new Access(new RoutingSourceLoader(new FileSourceLoader(workingDirectory),new HttpSourceLoader(store,metadata,observer,proxy,clock)),
                store,observer);
    }
}
