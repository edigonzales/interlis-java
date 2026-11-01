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
                             CompilerServices services, TerminalCapabilities terminalCapabilities, Path homeDirectory) {
    /** Creates a validated instance. */
    public CliEnvironment {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        homeDirectory = Objects.requireNonNull(homeDirectory, "homeDirectory").toAbsolutePath().normalize();
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        Objects.requireNonNull(services, "services");
        Objects.requireNonNull(terminalCapabilities, "terminalCapabilities");
    }

    /** Explicit embedded hosts default to an isolated home under their supplied working directory. */
    public CliEnvironment(Path workingDirectory, InputStream in, PrintStream out,
                          PrintStream err, Map<String,String> environment, CompilerServices services,
                          TerminalCapabilities terminalCapabilities) {
        this(workingDirectory,in,out,err,environment,services,terminalCapabilities,workingDirectory);
    }

    /** Existing callers with supplied streams default to unknown/noninteractive stderr. */
    public CliEnvironment(Path workingDirectory, InputStream in, PrintStream out,
                          PrintStream err, Map<String, String> environment, CompilerServices services) {
        this(workingDirectory, in, out, err, environment, services, new TerminalCapabilities(false));
    }

    /** Uses isolated default compiler services; supplied streams remain caller-owned. */
    public CliEnvironment(Path workingDirectory, InputStream in, PrintStream out,
                          PrintStream err, Map<String, String> environment) {
        this(workingDirectory, in, out, err, environment, CompilerServices.defaults());
    }

    /**
     * Wraps the current host streams with explicit UTF-8 output, independent of
     * the Windows console code page or JVM stdout/stderr encoding properties.
     * Neither the caller nor the CLI may close these host-owned streams.
     *
     * @return an environment with isolated default compiler services
     */
    public static CliEnvironment system() {
        // PrintStream.write(byte[]) passes these UTF-8 bytes through the existing
        // host streams; no global stream replacement or locale mutation is needed.
        return new CliEnvironment(Path.of("").toAbsolutePath(), System.in,
                new PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8),
                new PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8), System.getenv(),
                CompilerServices.defaults(), TerminalCapabilities.system(), Path.of(System.getProperty("user.home")));
    }
}
