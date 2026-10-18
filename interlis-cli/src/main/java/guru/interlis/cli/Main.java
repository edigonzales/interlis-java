package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;

/** Process entry; only this class maps the result to System.exit. */
public final class Main {
    /**
     * Returns the {@code Main} value.
     *
     * @return the value
     */
    private Main() {
    }

    /**
     * Executes {@code main}.
     *
     * @param args the {@code args} argument
     */
    public static void main(String[] args) {
        CliExecutionResult result = new DefaultCompilerCli().run(java.util.List.of(args), CliEnvironment.system(),
                CancellationToken.none());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }
}
