package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, null-free URL association and final redirect provenance. downloadedAt
 * is the last successful full download; EPOCH denotes unknown legacy age, never
 * fresh online. HTTP validators are neither stored nor used.
 */
public record RepositoryCacheMetadata(SourceId source, SourceId responseSource, ContentDigest digest,
                                      Instant downloadedAt) {
    public RepositoryCacheMetadata {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(responseSource, "responseSource");
        Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(downloadedAt, "downloadedAt");
    }
}
