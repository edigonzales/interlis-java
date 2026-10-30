package guru.interlis.cli;

import guru.interlis.execution.CancellationToken;
import java.util.List;

/** Explicit legacy checking entry; runtime compilation still uses the new shared engine. */
public final class Ili2cMain {
    private Ili2cMain() { }
    public static void main(String[] arguments) {
        var result = new DefaultCompilerCli(LegacyCliProfile.ili2c569()).run(List.of(arguments),
                CliEnvironment.system(), CancellationToken.none());
        if (result.exitCode() != 0) System.exit(result.exitCode());
    }
}
