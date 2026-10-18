package guru.interlis.cli;

import guru.interlis.diagnostics.Diagnostic;

import java.util.List;
import java.util.Objects;

/** Complete in-process CLI result. */
public record CliExecutionResult(int exitCode, CliTermination termination,
                                 List<Diagnostic> diagnostics, String output) {
    /** Creates a validated instance. */
    public CliExecutionResult {
        Objects.requireNonNull(termination, "termination");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(output, "output");
    }
}
