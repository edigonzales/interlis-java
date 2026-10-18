package guru.interlis.repository;

import java.util.Objects;

/**
 * Host-side source locator; the compiler never sees it.
 *
 * @param location the {@code location} component
 */
public record SourceLocator(String location) {
    /** Creates a validated instance. */
    public SourceLocator {
        Objects.requireNonNull(location, "location");
        if (location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }
}
