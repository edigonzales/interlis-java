package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.util.Objects;
import java.util.function.Consumer;

/** One explicit observation adapter; never stores run state or changes loading on callback failure. */
final class CacheObservations implements Consumer<RepositoryCacheObservation> {
    private final Consumer<RepositoryCacheObservation> observer;
    private CacheObservations(Consumer<RepositoryCacheObservation> observer) { this.observer=Objects.requireNonNull(observer,"observer"); }
    static CacheObservations detailed(Consumer<RepositoryCacheObservation> observer) { return new CacheObservations(observer); }
    static CacheObservations legacy(Consumer<RepositoryCacheEvent> observer) {
        Objects.requireNonNull(observer,"observer");
        return detailed(observation -> observer.accept(observation.event()));
    }
    void publish(RepositoryCacheEvent event, SourceId source) { accept(RepositoryCacheObservation.forSource(event,source)); }
    @Override public void accept(RepositoryCacheObservation observation) {
        try { observer.accept(observation); } catch(RuntimeException ignored) { /* observation only */ }
    }
}
