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
            if (command.has("--write")) targets.addAll(sources);
            stdout = !command.has("--write") && !command.has("--check");
        }
        command.value("--write-lock").ifPresent(value -> {
            Path target = local(value, environment);
            if (sources.contains(target)) throw new CliUsageException("lock output collides with a source");
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
}
