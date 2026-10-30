package guru.interlis.cli;

import java.nio.file.Path;
import java.util.*;

/** Host path and output-mode validation before any artifact is written. */
public final class OutputPlanner {
    /** Paths are resolved against the injected directory, including file URI inputs. */
    public OutputPlan plan(ParsedCommand command, CliEnvironment environment,
                           Optional<LegacyCliProfile> profile) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(profile, "profile");
        var sources = command.files().stream().map(value -> local(value, environment)).toList();
        var targets = new ArrayList<Path>();
        boolean stdout = profile.isEmpty();
        if (command.command().equals("format")) {
            if (command.has("--check") && command.has("--write"))
                throw new CliUsageException("--check and --write are mutually exclusive");
            if (new HashSet<>(sources).size() != sources.size())
                throw new CliUsageException("duplicate formatting source");
            var realPaths = new HashSet<Path>();
            var fileKeys = new HashSet<Object>();
            var withoutKeys = new ArrayList<Path>();
            for (Path source : sources) {
                try {
                    Path real = source.toRealPath();
                    Object key = java.nio.file.Files.readAttributes(real,
                            java.nio.file.attribute.BasicFileAttributes.class).fileKey();
                    if (!realPaths.add(real) || (key != null && !fileKeys.add(key))
                            || withoutKeys.stream().anyMatch(previous -> sameFile(previous, source)))
                        throw new CliUsageException("duplicate formatting source");
                    if (key == null) withoutKeys.add(real);
                } catch (java.io.IOException failure) { throw loadFailure(failure); }
            }
            if (command.has("--write")) targets.addAll(sources);
            stdout = !command.has("--write") && !command.has("--check");
        }
        command.value("--write-lock").ifPresent(value -> {
            Path target = local(value, environment);
            if (sources.stream().anyMatch(source -> sameFile(source, target)))
                throw new CliUsageException("lock output collides with a source");
            targets.add(target);
        });
        return new OutputPlan(targets, stdout);
    }

    private static Path local(String value, CliEnvironment environment) {
        try {
            Path path = value.startsWith("file:") ? Path.of(java.net.URI.create(value)) : Path.of(value);
            return environment.workingDirectory().resolve(path).toAbsolutePath().normalize();
        } catch (IllegalArgumentException invalid) { throw new CliUsageException("invalid local output path"); }
    }

    static boolean sameFile(Path first, Path second) {
        if (first.equals(second)) return true;
        try {
            return java.nio.file.Files.exists(first) && java.nio.file.Files.exists(second)
                    && java.nio.file.Files.isSameFile(first, second);
        } catch (java.io.IOException failure) { throw loadFailure(failure); }
    }

    private static guru.interlis.repository.SourceLoadException loadFailure(java.io.IOException failure) {
        var reason = failure instanceof java.nio.file.NoSuchFileException
                ? guru.interlis.repository.SourceLoadException.Reason.NOT_FOUND
                : failure instanceof java.nio.file.AccessDeniedException
                ? guru.interlis.repository.SourceLoadException.Reason.ACCESS_DENIED
                : guru.interlis.repository.SourceLoadException.Reason.IO_FAILURE;
        return new guru.interlis.repository.SourceLoadException(reason, "source identity could not be inspected", failure);
    }
}
