package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;

import java.util.Optional;

/**
 * Produces immutable repository catalogs. A previous catalog is returned
 * unchanged when the new revision is identical; running analyses never observe
 * a mutated catalog.
 */
public final class RepositoryRefreshService {
    private final RepositoryClient client;

    /**
     * Creates a refresh service.
     *
     * @param client discovery client
     */
    public RepositoryRefreshService(RepositoryClient client) {
        this.client = java.util.Objects.requireNonNull(client, "client");
    }

    /**
     * Refreshes the catalog.
     *
     * @param config repository configuration
     * @param previousState optional previous catalog
     * @param token cancellation signal
     * @return immutable catalog
     */
    public RepositoryCatalog refresh(RepositoryConfig config,
                                     Optional<RepositoryCatalog> previousState,
                                     CancellationToken token) {
        RepositoryCatalog fresh = client.catalog(config, token);
        if (previousState.isPresent()
                && previousState.orElseThrow().revision().equals(fresh.revision())) {
            return previousState.orElseThrow();
        }
        return fresh;
    }
}
