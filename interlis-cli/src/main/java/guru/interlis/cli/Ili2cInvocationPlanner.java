package guru.interlis.cli;

import guru.interlis.compiler.ModelSelector;
import guru.interlis.execution.*;
import guru.interlis.repository.SourceLoadException;
import guru.interlis.source.SourceId;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Host discovery only; model headers, imports and binding remain in the shared compiler. */
final class Ili2cInvocationPlanner {
    record Plan(List<String> arguments, List<ModelSelector> roots,
                boolean quiet, boolean withoutWarnings, boolean trace, boolean checking) {
        Plan { arguments = List.copyOf(arguments); roots = List.copyOf(roots); }
    }

    Plan prepare(List<String> arguments, LegacyCliProfile profile, CliEnvironment environment,
                 CancellationToken token) {
        var supplied = new ArrayList<String>(); supplied.add("ili2c"); supplied.addAll(arguments);
        var outcome = new DefaultCommandLineParser().parse(supplied, profile.commandSpec());
        if (outcome instanceof CliParseOutcome.InvalidCommandLine invalid) throw new CliUsageException(invalid.message());
        var command = (ParsedCommand) outcome;
        boolean quiet = command.has("--quiet"), warnings = command.has("--without-warnings"), trace = command.has("--trace");
        if (command.has("-h") || command.has("--help") || command.has("-u") || command.has("--usage"))
            return new Plan(List.of("help"), List.of(), quiet, warnings, trace, false);
        if (command.has("-v") || command.has("--version"))
            return new Plan(List.of("version"), List.of(), quiet, warnings, trace, false);
        if (command.files().isEmpty()) throw new CliUsageException("no ili files or root models given; GUI is outside this profile");
        var paths = new LinkedHashSet<Path>();
        var directories = new LinkedHashSet<Path>();
        var roots = new ArrayList<ModelSelector>();
        for (String value : command.files()) {
            if (value.startsWith("http://") || value.startsWith("https://"))
                throw new CliUsageException("direct URL sources are outside this profile; use --modeldir and a root model name");
            Path path = local(value, environment);
            if (value.endsWith(".ili") || value.contains("/") || value.contains("\\") || Files.isRegularFile(path)) {
                paths.add(path); directories.add(path.getParent());
                roots.add(new ModelSelector.BySource(new SourceId(path.toUri().toString())));
            } else roots.add(new ModelSelector.ByName(value, Optional.empty(), Optional.empty()));
        }
        if (directories.isEmpty()) directories.add(environment.workingDirectory().toAbsolutePath().normalize());
        var nativeArguments = new ArrayList<String>(); nativeArguments.add("check");
        if (!command.has("--no-auto")) {
            String search = command.value("--modeldir").orElseGet(profile::defaultModelDirectories);
            for (String entry : search.split(";", -1)) {
                token.throwIfCancellationRequested();
                if (entry.isBlank()) throw new CliUsageException("empty model directory");
                if (entry.startsWith("https://") || entry.startsWith("http://")) {
                    nativeArguments.add("--repository"); nativeArguments.add(entry);
                } else if (entry.equals("%ILI_DIR")) {
                    for (Path directory : directories) scan(directory, paths, token);
                } else if (entry.equals("%JAR_DIR")) {
                    try {
                        Path location = Path.of(LegacyCliProfile.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                        Path directory = Files.isDirectory(location) ? location : location.getParent();
                        if (Files.isDirectory(directory)) scan(directory, paths, token);
                    } catch (java.net.URISyntaxException invalid) { throw new CliUsageException("invalid launcher location"); }
                } else scan(local(entry, environment), paths, token);
            }
        }
        nativeArguments.add("--");
        paths.forEach(path -> nativeArguments.add(path.toString()));
        return new Plan(nativeArguments, roots, quiet, warnings, trace, true);
    }

    private static Path local(String value, CliEnvironment environment) {
        try { return environment.workingDirectory().resolve(value).toAbsolutePath().normalize(); }
        catch (InvalidPathException invalid) { throw new CliUsageException("invalid model path"); }
    }

    private static void scan(Path directory, Set<Path> sources, CancellationToken token) {
        if (!Files.isDirectory(directory)) throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,
                "model directory is unavailable", null);
        try (var paths = Files.list(directory)) {
            var iterator = paths.limit(10_001).sorted().iterator();
            int entries = 0;
            while (iterator.hasNext()) {
                token.throwIfCancellationRequested();
                if (++entries > 10_000 || sources.size() > 10_000)
                    throw new OperationCancelledException(CancellationReason.RESOURCE_LIMIT);
                Path path = iterator.next();
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".ili")) sources.add(path);
            }
        } catch (IOException failure) { throw new SourceLoadException(SourceLoadException.Reason.IO_FAILURE,
                "model directory could not be read", failure); }
    }
}
