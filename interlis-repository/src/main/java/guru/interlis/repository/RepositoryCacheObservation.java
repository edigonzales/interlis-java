package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable host cache observation. The optional display address is stripped of
 * user information, query and fragment before publication. It is deliberately
 * not a SourceId and must not be used for loading, cache keys or model binding.
 * Global cache write failures may omit the address. No HTTP headers are exposed.
 */
public record RepositoryCacheObservation(RepositoryCacheEvent event, Optional<String> safeSource) {
    public RepositoryCacheObservation {
        Objects.requireNonNull(event, "event");
        safeSource = Objects.requireNonNull(safeSource, "safeSource").map(RepositorySearchStep::safeUri);
    }
    public static RepositoryCacheObservation forSource(RepositoryCacheEvent event, SourceId source) {
        return new RepositoryCacheObservation(event, Optional.of(Objects.requireNonNull(source,"source").value()));
    }
    public static RepositoryCacheObservation global(RepositoryCacheEvent event) {
        return new RepositoryCacheObservation(event, Optional.empty());
    }
}
