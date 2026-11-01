package guru.interlis.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable host output locations; planning does not create or truncate files. */
public record OutputPlan(List<Path> fileTargets, boolean writesStdout) {
    public OutputPlan { fileTargets = List.copyOf(Objects.requireNonNull(fileTargets, "fileTargets")); }
}
