package guru.interlis.repository;

import guru.interlis.source.SourceId;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable URL-to-content association. responseSource scopes validators to the final
 * redirect target; source retains the original identity including query parameters.
 * Validators are opaque HTTP header values, never public diagnostic text.
 */
public record RepositoryCacheMetadata(SourceId source, SourceId responseSource, ContentDigest digest,
                                      Optional<String> etag, Optional<String> lastModified) {
    public RepositoryCacheMetadata {
        Objects.requireNonNull(source, "source"); Objects.requireNonNull(responseSource, "responseSource");
        Objects.requireNonNull(digest, "digest");
        etag = header(etag); lastModified = header(lastModified);
    }
    private static Optional<String> header(Optional<String> value) {
        Objects.requireNonNull(value, "header");
        if (value.filter(v -> v.length() > 8192 || v.chars().anyMatch(c -> c < 32 || c == 127 || c > 255)).isPresent())
            throw new IllegalArgumentException("invalid cache validator");
        return value;
    }
}
