package guru.interlis.gui.application;

import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.ContentDigest;
import guru.interlis.repository.HostRepositories;
import guru.interlis.repository.IndexPayload;
import guru.interlis.repository.LoadedSource;
import guru.interlis.repository.RepositoryBase;
import guru.interlis.repository.RepositoryCacheRequest;
import guru.interlis.repository.RepositoryIndexException;
import guru.interlis.repository.RepositoryIndexReader;
import guru.interlis.repository.RepositorySource;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLoader;
import guru.interlis.repository.SourceLocator;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Background browser adapter. It loads only explicitly requested indexes or one preview source. */
public final class RepositoryCatalogService {
    /** Validates a user-specified directory or credential-free HTTP(S) base. */
    public RepositorySource repository(String location, Path directory, CancellationToken token) {
        token.throwIfCancellationRequested();
        String text = location.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Enter a directory or HTTP(S) repository base.");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        URI base;
        if (lower.startsWith("http:") || lower.startsWith("https:")) {
            URI uri;
            try {
                uri = URI.create(text).normalize();
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Enter a valid HTTP(S) repository base.");
            }
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("Use an HTTP(S) base without credentials, query or fragment.");
            }
            String path = uri.getRawPath();
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            base = URI.create(scheme + "://" + uri.getRawAuthority() + path + (path.endsWith("/") ? "" : "/"));
        } else {
            if (text.contains("://") && !lower.startsWith("file:")) {
                throw new IllegalArgumentException("Only directories and HTTP(S) repositories are supported.");
            }
            Path path;
            try {
                path = lower.startsWith("file:") ? Path.of(URI.create(text)) : directory.resolve(text).normalize();
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Enter a valid repository directory.");
            }
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("The repository directory is unavailable.");
            }
            base = path.toAbsolutePath().normalize().toUri();
        }
        token.throwIfCancellationRequested();
        return new RepositorySource(new RepositoryBase(base), new SourceLocator(base.resolve("ilimodels.xml").toString()));
    }

    /** Returns one result per requested repository, including isolated load failures. */
    public List<RepositoryIndexSnapshot> load(List<RepositorySource> repositories, SessionOptions options, Path directory,
                                        long generation, CancellationToken token) {
        var loader = host(directory, options);
        var catalogs = new ArrayList<RepositoryIndexSnapshot>();
        for (RepositorySource repository : repositories) {
            token.throwIfCancellationRequested();
            catalogs.add(loadOne(loader, repository, options, generation, token));
        }
        return List.copyOf(catalogs);
    }

    private static RepositoryIndexSnapshot loadOne(SourceLoader loader, RepositorySource repository, SessionOptions options,
                                             long generation, CancellationToken token) {
        try {
            var loaded = fetch(loader, repository.indexLocator(), options, RepositoryCacheRequest.metadata(), token);
            var index = new RepositoryIndexReader().read(new IndexPayload(loaded.text()), repository.base());
            token.throwIfCancellationRequested();
            var digest = new ContentDigest("sha256", loaded.byteSha256());
            var entries = new ArrayList<RepositoryIndexSnapshot.Entry>();
            for (int indexPosition = 0; indexPosition < index.models().size(); indexPosition++) {
                token.throwIfCancellationRequested();
                entries.add(new RepositoryIndexSnapshot.Entry(new RepositoryIndexSnapshot.EntryId(repository.base(), digest, indexPosition,
                        generation), index.models().get(indexPosition)));
            }
            return new RepositoryIndexSnapshot(repository, entries.isEmpty() ? RepositoryIndexSnapshot.State.EMPTY : RepositoryIndexSnapshot.State.LOADED,
                    entries, Optional.empty(), Optional.of(loaded.origin()));
        } catch (SourceLoadException failure) {
            return new RepositoryIndexSnapshot(repository, failure.reason() == SourceLoadException.Reason.OFFLINE
                    ? RepositoryIndexSnapshot.State.OFFLINE_UNAVAILABLE : RepositoryIndexSnapshot.State.UNAVAILABLE, List.of(),
                    Optional.of(failure.getMessage()), Optional.empty());
        } catch (RepositoryIndexException failure) {
            return new RepositoryIndexSnapshot(repository, RepositoryIndexSnapshot.State.INVALID, List.of(),
                    Optional.of(failure.getMessage()), Optional.empty());
        }
    }

    /** The preview revision is scoped to the browser request and is never used for a compiler snapshot. */
    public CompileResult.SourceSnapshot preview(RepositoryIndexSnapshot.Entry entry, SessionOptions options, Path directory,
                                                 long revision, CancellationToken token) {
        var locator = new SourceLocator(entry.metadata().location().toString());
        var loaded = fetch(host(directory, options), locator, options, RepositoryCacheRequest.model(entry.metadata().md5()), token);
        var source = new SourceText(loaded.sourceId(), new SourceRevision(revision), loaded.text());
        return new CompileResult.SourceSnapshot(source, Optional.of(new ContentDigest("sha256", loaded.byteSha256())),
                CompileResult.Origin.REPOSITORY);
    }

    private static SourceLoader host(Path directory, SessionOptions options) {
        return HostRepositories.createObserved(directory, options.cache(), ignored -> { }, options.proxy()).loader();
    }

    private static LoadedSource fetch(SourceLoader loader, SourceLocator locator, SessionOptions options,
                                      RepositoryCacheRequest request, CancellationToken token) {
        var policy = options.repositories().fetchPolicy();
        return policy.offline() ? loader.loadCached(locator, policy, token) : loader.load(locator, policy, request, token);
    }
}
