package guru.interlis.cli;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.repository.ContentDigest;
import guru.interlis.repository.FetchPolicy;
import guru.interlis.repository.FileSourceLoader;
import guru.interlis.repository.ResolutionLock;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.repository.SourceLocator;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.SourceChange;
import guru.interlis.workspace.SourceExpectation;
import guru.interlis.workspace.SourceUpdate;
import guru.interlis.workspace.Workspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Discovers loose model files beside the explicitly supplied sources. */
final class LocalModelDiscovery {
    private final FileSourceLoader loader;

    LocalModelDiscovery(CliEnvironment environment) {
        loader = new FileSourceLoader(environment.workingDirectory());
    }

    void addNeighbours(Workspace workspace, Set<SourceId> inputs, Optional<ResolutionLock> lock,
                       Map<SourceId, ContentDigest> sourcePins, Consumer<Path> directorySearch,
                       CancellationToken token) {
        var directories = new LinkedHashSet<Path>();
        inputs.forEach(id -> directories.add(Path.of(URI.create(id.value())).getParent()));
        var budget = workspace.configuration().compilerOptions().budget();
        long remainingEntries = budget.maxSemanticWorkUnits();
        var paths = new LinkedHashSet<Path>();
        for (Path directory : directories) {
            token.throwIfCancellationRequested();
            directorySearch.accept(directory);
            try (var entries = Files.list(directory)) {
                for (var iterator = entries.iterator(); iterator.hasNext();) {
                    token.throwIfCancellationRequested();
                    if (--remainingEntries < 0) throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                    Path path = iterator.next();
                    var id = new SourceId(path.toUri().toString());
                    if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ili")
                            && !inputs.contains(id) && Files.isRegularFile(path)) paths.add(path);
                }
            } catch (IOException | UncheckedIOException failure) {
                throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                        "source directory could not be read", failure);
            }
        }

        var frozenSources = lock.stream().flatMap(frozen -> frozen.models().values().stream())
                .flatMap(java.util.Collection::stream).map(pin -> pin.source()).collect(java.util.stream.Collectors.toSet());
        long total = inputs.stream().mapToLong(id -> workspace.source(id).orElseThrow().text().length()).sum();
        for (Path path : paths.stream().sorted().toList()) {
            token.throwIfCancellationRequested();
            var id = new SourceId(path.toUri().toString());
            if (lock.isPresent() && !frozenSources.contains(id)) continue;
            var source = loader.load(new SourceLocator(id.value()), FetchPolicy.defaults(), token);
            total += source.text().length();
            if (total > budget.maxTotalSourceUtf16Length() || source.text().length() > budget.maxSourceUtf16Length())
                throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
            workspace.update(new SourceUpdate(id, new SourceExpectation.Absent(), new SourceChange.Replace(source.text())));
            sourcePins.putIfAbsent(id, new ContentDigest("sha256", source.byteSha256()));
        }
    }
}
