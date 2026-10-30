package guru.interlis.cli;

import guru.interlis.compiler.CompilerServices;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Explicit CLI environment; no global streams inside the application object. */
public record CliEnvironment(Path workingDirectory, InputStream in, PrintStream out,
                             PrintStream err, Map<String, String> environment,
                             CompilerServices services) {
    /** Creates a validated instance. */
    public CliEnvironment {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        Objects.requireNonNull(services, "services");
    }

    /** Uses isolated default compiler services; supplied streams remain caller-owned. */
    public CliEnvironment(Path workingDirectory, InputStream in, PrintStream out,
                          PrintStream err, Map<String, String> environment) {
        this(workingDirectory, in, out, err, environment, CompilerServices.defaults());
    }

    /**
     * Returns the {@code system} value.
     *
     * @return the value
     */
    public static CliEnvironment system() {
        return new CliEnvironment(Path.of("").toAbsolutePath(), System.in, System.out,
                System.err, System.getenv());
    }
}
