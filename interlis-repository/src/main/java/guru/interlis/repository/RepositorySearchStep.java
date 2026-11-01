package guru.interlis.repository;

import java.net.URI;
import java.util.Objects;

/** One ordered, credential-free search decision. Detail preserves raw version strings, never response bodies. */
public record RepositorySearchStep(String modelName, URI repository, Kind kind, String detail) {
    public RepositorySearchStep {
        Objects.requireNonNull(modelName,"modelName"); Objects.requireNonNull(repository,"repository");
        Objects.requireNonNull(kind,"kind"); Objects.requireNonNull(detail,"detail");
        repository = URI.create(safeUri(repository.toString()));
    }
    public enum Kind { LOOKUP, SELECTED, DUPLICATE_ROOT, DUPLICATE_SUCCESSOR, BROKEN_CHAIN, NO_ROOT, REPOSITORY_UNAVAILABLE, SITE_UNAVAILABLE }
    public boolean warning() { return kind != Kind.LOOKUP && kind != Kind.SELECTED; }
    /** Removes credentials, queries and fragments from public explanations. Exact identities remain in pins. */
    public static String safeUri(String value) {
        try {
            var uri=URI.create(value);
            return new URI(uri.getScheme(),null,uri.getHost(),uri.getPort(),uri.getPath(),null,null).toString();
        } catch (java.net.URISyntaxException | IllegalArgumentException invalid) { return "<source>"; }
    }
}
