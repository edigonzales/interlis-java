package guru.interlis.repository;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Explicit JVM composition; create one access per invocation, with no global cache or run state. */
public final class HostRepositories {
    private HostRepositories() { }
    /** Loader/store share one cache. Constructors alone perform no filesystem IO. */
    public record Access(SourceLoader loader, ContentStore contentStore, Consumer<RepositoryCacheEvent> observer) {
        public Access { Objects.requireNonNull(loader); Objects.requireNonNull(contentStore); Objects.requireNonNull(observer); }
        public RepositoryClosureService closureService() { return new RepositoryClosureService(loader,contentStore,observer); }
    }
    /**
     * Builds host adapters. Observations contain no URLs, headers or paths; callback
     * failures never change loading. Persistent write failures are reported once per access.
     */
    public static Access create(Path workingDirectory, RepositoryCacheConfiguration configuration,
                                Consumer<RepositoryCacheEvent> observer) {
        Objects.requireNonNull(workingDirectory); Objects.requireNonNull(configuration); Objects.requireNonNull(observer);
        Consumer<RepositoryCacheEvent> safe = event -> { try { observer.accept(event); } catch(RuntimeException ignored) { /* observation only */ } };
        var warned = new AtomicBoolean();
        Runnable unavailable = () -> { if(warned.compareAndSet(false,true))safe.accept(RepositoryCacheEvent.WRITE_UNAVAILABLE); };
        ContentStore store=configuration.directory().<ContentStore>map(p -> new DirectoryContentStore(p,16_777_216,unavailable)).orElseGet(InMemoryContentStore::new);
        var metadata=configuration.directory().map(p -> new DirectoryMetadataStore(p,unavailable)).orElse(null);
        return new Access(new RoutingSourceLoader(new FileSourceLoader(workingDirectory),new HttpSourceLoader(store,metadata,safe)),store,safe);
    }
}
