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
        var application = new DefaultCompilerCli();
        var arguments = Boolean.getBoolean("guru.interlis.cli.windowsArguments")
                ? windowsArguments() : java.util.List.of(args);
        CliExecutionResult result = application.run(arguments, hostEnvironment(),
                CancellationToken.none());
        if (result.exitCode() != 0) {
            System.exit(result.exitCode());
        }
    }

    /** Keeps caller-relative paths while the Windows launcher resolves modules locally. */
    private static CliEnvironment hostEnvironment() {
        var current = CliEnvironment.system();
        if (!Boolean.getBoolean("guru.interlis.cli.windowsArguments")) return current;
        String caller = System.getenv("_INTERLIS_COMPILER_WORKING_DIRECTORY");
        if (caller == null) return current; // Existing programmatic bridge callers remain compatible.
        return new CliEnvironment(java.nio.file.Path.of(caller),current.in(),current.out(),current.err(),
                current.environment(),current.services(),current.terminalCapabilities());
    }

    /** Reads the Windows launcher's ordered Unicode arguments, including empty values. */
    private static java.util.List<String> windowsArguments() {
        int count = Integer.parseInt(System.getenv("_INTERLIS_COMPILER_ARG_COUNT"));
        var arguments = new java.util.ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            // Windows deletes an environment variable when SET assigns an empty value.
            arguments.add(java.util.Objects.requireNonNullElse(
                    System.getenv("_INTERLIS_COMPILER_ARG_" + index), ""));
        }
        return java.util.List.copyOf(arguments);
    }
}
