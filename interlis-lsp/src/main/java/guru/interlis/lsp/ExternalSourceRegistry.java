package guru.interlis.lsp;

import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Session-scoped immutable readonly handles. All access is on the owner queue. */
final class ExternalSourceRegistry {
    record Entry(String uri, SourceText source, String digest) { }

    private final String session = UUID.randomUUID().toString();
    private final Map<SourceId, Entry> current = new LinkedHashMap<>();
    private final Map<String, Entry> handles = new LinkedHashMap<>();
    private final int maxHandles;
    private long units;

    ExternalSourceRegistry() {
        this(10_000);
    }

    ExternalSourceRegistry(int maxHandles) {
        if (maxHandles <= 0) {
            throw new IllegalArgumentException("maxHandles must be positive");
        }
        this.maxHandles = maxHandles;
    }

    /**
     * Checks the complete batch and constructs its URI prefixes before either the
     * workspace or registry changes. The caller must install it on the same owner
     * action using the workspace-assigned revisions.
     */
    Map<SourceId, String> prepare(Map<SourceId, String> sources, long limit) {
        if (sources.isEmpty()) {
            return Map.of();
        }
        long remaining = limit - units;
        if (sources.size() > maxHandles - handles.size()) {
            throw capacityExceeded();
        }
        for (String text : sources.values()) {
            remaining -= text.length();
            if (remaining < 0) {
                throw capacityExceeded();
            }
        }
        var prefixes = new LinkedHashMap<SourceId, String>();
        sources.keySet().forEach(id -> prefixes.put(id, uriPrefix(id)));
        return prefixes;
    }

    /** Installs a preflighted source and reports whether it replaces a handle. */
    boolean install(SourceText source, String prefix) {
        String uri = prefix + '?' + source.revision().value();
        var entry = new Entry(uri, source, source.contentFingerprint().value());
        var previous = current.put(source.id(), entry);
        handles.put(uri, entry);
        units += source.length();
        return previous != null;
    }

    boolean register(SourceText source, long limit) {
        var old = current.get(source.id());
        if (old != null && old.source().equals(source)) {
            return false;
        }
        var prefixes = prepare(Map.of(source.id(), source.text()), limit);
        return install(source, prefixes.get(source.id()));
    }

    private String uriPrefix(SourceId id) {
        String path = URI.create(id.value()).getPath();
        String name = path == null ? "source.ili" : path.substring(path.lastIndexOf('/') + 1);
        if (name.isBlank() || !name.toLowerCase(Locale.ROOT).endsWith(".ili")) {
            name = "source.ili";
        }
        try {
            return new URI("interlis-source", null, "/" + session + "/" + UUID.randomUUID() + "/" + name,
                    null, null).toASCIIString();
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("invalid readonly source name", invalid);
        }
    }

    private static RuntimeException capacityExceeded() {
        return LspFailures.error(ResponseErrorCode.RequestFailed, "readonly source registry limit reached");
    }

    boolean contains(SourceId id) { return current.containsKey(id); }
    Optional<String> currentUri(SourceId id) { return Optional.ofNullable(current.get(id)).map(Entry::uri); }
    Optional<Entry> lookup(String uri) { return Optional.ofNullable(handles.get(uri)); }

    Map<SourceId, String> uris() {
        var result = new LinkedHashMap<SourceId, String>();
        current.forEach((id, entry) -> result.put(id, entry.uri()));
        return Map.copyOf(result);
    }

    Entry read(String uri, String revision) {
        var entry = lookup(uri).orElseThrow(() ->
                LspFailures.error(ResponseErrorCode.InvalidParams, "unknown readonly source handle"));
        if (!Long.toString(entry.source().revision().value()).equals(revision)) {
            throw LspFailures.error(ResponseErrorCode.ContentModified, "readonly source revision differs");
        }
        return entry;
    }

    void close() { current.clear(); handles.clear(); units = 0; }
}
