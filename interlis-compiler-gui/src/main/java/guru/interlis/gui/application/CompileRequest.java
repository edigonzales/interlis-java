package guru.interlis.gui.application;

import guru.interlis.execution.RunContext;

import java.nio.file.Path;
import java.util.Objects;

/** Captures one non-null, immutable request and its session generation at launch time. */
public record CompileRequest(RunContext context, long generation, SessionInputs inputs,
                             SessionOptions options, Path workingDirectory) {
    public CompileRequest {
        Objects.requireNonNull(context);
        Objects.requireNonNull(inputs);
        Objects.requireNonNull(options);
        workingDirectory = Objects.requireNonNull(workingDirectory).toAbsolutePath().normalize();
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("A compile request needs an input file or explicit root");
        }
    }
}
