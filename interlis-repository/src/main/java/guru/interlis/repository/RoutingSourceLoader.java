package guru.interlis.repository;

import guru.interlis.execution.CancellationToken;
import java.nio.file.Path;
import java.util.Objects;

/** Explicit JVM loader for paths, file URIs and HTTP(S); no scheme guessing in the core. */
public final class RoutingSourceLoader implements SourceLoader {
    private final SourceLoader files;
    private final SourceLoader http;
    public RoutingSourceLoader(Path workingDirectory) {
        this(new FileSourceLoader(workingDirectory), new HttpSourceLoader());
    }
    public RoutingSourceLoader(SourceLoader files, SourceLoader http) {
        this.files = Objects.requireNonNull(files, "files");
        this.http = Objects.requireNonNull(http, "http");
    }
    @Override public LoadedSource load(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        return load(locator, policy, RepositoryCacheRequest.unconditional(), token);
    }
    @Override public LoadedSource load(SourceLocator locator, FetchPolicy policy, RepositoryCacheRequest request, CancellationToken token) {
        Objects.requireNonNull(request, "request");
        String location = locator.location();
        if (location.startsWith("https:") || location.startsWith("http:")) return http.load(locator, policy, request, token);
        if (location.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !location.startsWith("file:")) {
            throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE, "unsupported source scheme", null);
        }
        return files.load(locator, policy, token);
    }
    @Override public LoadedSource loadCached(SourceLocator locator, FetchPolicy policy, CancellationToken token) {
        String location=Objects.requireNonNull(locator).location();
        if(location.startsWith("https:") || location.startsWith("http:"))return http.loadCached(locator,policy,token);
        return SourceLoader.super.loadCached(locator,policy,token);
    }
}
