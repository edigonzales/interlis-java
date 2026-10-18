package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;

import java.util.List;

/** In-process CLI entry; implementations never call System.exit. */
public interface CliApplication {
    /**
     * Runs one in-process CLI invocation.
     *
     * <p>The returned result carries the termination reason and exit code; the
     * application never calls {@link System#exit(int)}.</p>
     *
     * @param arguments raw CLI arguments
     * @param environment host streams/configuration
     * @param cancellation cancellation signal
     * @return typed termination result
     */
    CliExecutionResult run(List<String> arguments, CliEnvironment environment,
                           CancellationToken cancellation);
}
