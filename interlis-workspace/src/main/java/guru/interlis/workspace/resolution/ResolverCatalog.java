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
        Objects.requireNonNull(entries,"entries").forEach((key,value)->{Objects.requireNonNull(key,"key");Objects.requireNonNull(value,"value");});
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
        var canonical=new StringBuilder("resolver-catalog-v2");canonical.append(entries.size()).append(':');
        entries.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            canonical.append(e.getKey().length()).append(':').append(e.getKey());
            canonical.append(e.getValue().length()).append(':').append(e.getValue());
        });
        return "sha256:"+guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digestUtf16(canonical.toString()));
    }
}
