package guru.interlis.repository;

import java.net.URI;
import java.nio.file.Path;

/** Host-only lexical file URI normalization. No symlink resolution or content access. */
final class SourceUris {
    private SourceUris() { }
    static String canonicalFile(String value) {
        if (!value.startsWith("file:")) return value;
        return Path.of(URI.create(value)).toAbsolutePath().normalize().toUri().toString();
    }
    static java.util.Map<guru.interlis.source.SourceId, ContentDigest> pins(java.util.Map<guru.interlis.source.SourceId, ContentDigest> values) {
        var normalized = new java.util.LinkedHashMap<guru.interlis.source.SourceId, ContentDigest>();
        java.util.Map.copyOf(values).forEach((id, digest) -> {
            var previous = normalized.putIfAbsent(new guru.interlis.source.SourceId(canonicalFile(id.value())), digest);
            if (previous != null && !previous.equals(digest)) throw new IllegalArgumentException("conflicting canonical source pins");
        });
        return java.util.Map.copyOf(normalized);
    }
}
