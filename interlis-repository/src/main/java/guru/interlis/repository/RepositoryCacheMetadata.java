package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, null-free URL association and final redirect provenance.
 * {@code downloadedAt} is the last successful full download. This value type is
 * separate from the host's current cache file format.
 */
public record RepositoryCacheMetadata(SourceId source, SourceId responseSource, ContentDigest digest,
                                      Instant downloadedAt) {
    public RepositoryCacheMetadata {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(responseSource, "responseSource");
        Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(downloadedAt, "downloadedAt");
    }
}
