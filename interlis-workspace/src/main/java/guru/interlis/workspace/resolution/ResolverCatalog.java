package guru.interlis.workspace.resolution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Frozen resolver catalog (empty in the in-memory P1 workspace).
 *
 * @param entries entries
 */
public record ResolverCatalog(Map<String, String> entries) {
    /** Creates a validated instance. */
    public ResolverCatalog {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(entries, "entries")));
    }

    /**
     * Returns the {@code empty} value.
     *
     * @return the value
     */
    public static ResolverCatalog empty() {
        return new ResolverCatalog(Map.of());
    }

    /**
     * Returns the {@code fingerprint} value.
     *
     * @return the value
     */
    public String fingerprint() {
        return entries.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .sorted()
                .collect(java.util.stream.Collectors.joining(",", "catalog[", "]"));
    }
}
