package guru.interlis.lsp;

import guru.interlis.lsp.ProjectSourceLoader.FileContent;
import guru.interlis.lsp.ProjectSourceLoader.FileIdentity;
import guru.interlis.source.SourceId;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Owner-queue catalog of local identities established by the file I/O adapter. */
final class LspFileIdentityRegistry {
    private static final int MAX_ALIAS_PATHS = 40_000;

    private record Alias(String uri, Optional<Object> fileKey) { }

    private final Map<String, FileContent> closedFiles = new LinkedHashMap<>();
    private final Map<Path, Alias> aliases = new LinkedHashMap<>();
    private final Map<Object, String> keys = new LinkedHashMap<>();
    private long identityRevision;

    /** Normalizes the spelling of a local URI without consulting the filesystem. */
    static Optional<Path> path(String uri) {
        try {
            var value = URI.create(uri);
            return "file".equalsIgnoreCase(value.getScheme())
                    ? Optional.of(Path.of(value).toAbsolutePath().normalize()) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    String canonicalUri(String uri) {
        return path(uri).map(aliases::get).map(Alias::uri).orElse(uri);
    }

    long identityRevision() {
        return identityRevision;
    }

    Optional<String> sourceForKey(FileIdentity identity) {
        return identity.fileKey().map(keys::get);
    }

    /** A surviving address on a keyless filesystem retains its previously proved association. */
    boolean isRegisteredKeylessAlias(FileIdentity identity, String canonicalUri) {
        var alias = aliases.get(identity.path());
        return identity.fileKey().isEmpty() && alias != null && alias.fileKey().isEmpty()
                && alias.uri().equals(canonicalUri);
    }

    void checkCapacity(List<FileIdentity> identities) {
        var additional = new LinkedHashSet<Path>();
        for (var identity : identities) {
            if (!aliases.containsKey(identity.path())) additional.add(identity.path());
            if (!aliases.containsKey(identity.realPath())) additional.add(identity.realPath());
        }
        checkCapacity(additional.size());
    }

    /** Registers only paths and keys obtained from a verified physical observation. */
    void rememberPhysicalIdentity(FileIdentity identity, String canonicalUri) {
        int added = aliases.containsKey(identity.path()) ? 0 : 1;
        if (!identity.path().equals(identity.realPath()) && !aliases.containsKey(identity.realPath())) added++;
        checkCapacity(added);

        var previous = aliases.get(identity.path());
        if (previous != null && previous.uri().equals(canonicalUri)
                && previous.fileKey().isPresent() && identity.fileKey().isPresent()
                && !previous.fileKey().equals(identity.fileKey())) {
            // The logical primary was replaced. Its old hardlinks are no longer aliases of the new file.
            var retiredKey = previous.fileKey().orElseThrow();
            aliases.values().removeIf(alias -> alias.uri().equals(canonicalUri) && alias.fileKey().equals(previous.fileKey()));
            keys.remove(retiredKey, canonicalUri);
            identityRevision++;
        }
        var alias = new Alias(canonicalUri, identity.fileKey());
        boolean changed = !alias.equals(aliases.put(identity.path(), alias));
        changed |= !alias.equals(aliases.put(identity.realPath(), alias));
        if (identity.fileKey().isPresent()) {
            changed |= !canonicalUri.equals(keys.put(identity.fileKey().orElseThrow(), canonicalUri));
        }
        if (changed) identityRevision++;
    }

    void forgetPath(String uri) {
        path(uri).ifPresent(address -> {
            if (aliases.remove(address) != null) identityRevision++;
        });
    }

    void installClosed(String uri, FileContent file) {
        closedFiles.put(uri, file);
    }

    /** Removes a source only after the host confirms that no physical address survives. */
    void removeClosed(String uri) {
        closedFiles.remove(uri);
        boolean changed = aliases.values().removeIf(alias -> alias.uri().equals(uri));
        changed |= keys.values().removeIf(uri::equals);
        if (changed) identityRevision++;
    }

    FileContent closedFile(String uri) {
        return closedFiles.get(uri);
    }

    /** Immutable captured metadata for query workers and file I/O. */
    Map<String, FileContent> installedClosedFiles() {
        return Map.copyOf(closedFiles);
    }

    void addClosedSourceUris(Map<SourceId, String> sources) {
        closedFiles.forEach((uri, file) -> sources.put(new SourceId(uri), file.path().toUri().toString()));
    }

    /** The current edit address is tried first, then the other registered physical paths. */
    List<String> aliasesOf(String canonicalUri) {
        var paths = new LinkedHashSet<String>();
        var primary = closedFiles.get(canonicalUri);
        if (primary != null) paths.add(primary.path().toUri().toString());
        aliases.forEach((path, alias) -> {
            if (canonicalUri.equals(alias.uri())) paths.add(path.toUri().toString());
        });
        return List.copyOf(paths);
    }

    void clear() {
        closedFiles.clear();
        aliases.clear();
        keys.clear();
    }

    private void checkCapacity(int added) {
        if (aliases.size() + added > MAX_ALIAS_PATHS) {
            throw LspFailures.error(ResponseErrorCode.RequestFailed, "file identity limit reached");
        }
    }
}
