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
        boolean standard = args.length > 0 && java.util.Set.of("help", "version", "capabilities", "check",
                "compile", "analyze", "diagram", "format").contains(args[0]);
        var application = standard ? new DefaultCompilerCli() : new DefaultCompilerCli(LegacyCliProfile.ili2c569());
        CliExecutionResult result = application.run(java.util.List.of(args), CliEnvironment.system(),
                CancellationToken.none());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }
}
