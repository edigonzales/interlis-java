package guru.interlis.source;

import java.util.Objects;

/**
 * Opaque, host-canonical source identity.
 *
 * <p>The value is compared by exact Java string equality. The core never
 * resolves paths, normalizes case or derives retrieval strategies from the
 * value. It must not be empty.</p>
 *
 * @param value opaque host-canonical identity, never empty
 */
public record SourceId(String value) {
    /** Creates a validated instance. */
    public SourceId {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("SourceId must not be empty");
        }
    }
}
