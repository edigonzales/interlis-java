package guru.interlis.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable correlation metadata for one run. It is not a started span and
 * carries no observer, clock, workspace or compiler request.
 */
public record RunContext(RunId runId, Optional<RunId> parentRunId,
                         Map<String, String> correlationAttributes) {
    /** Creates a validated instance. */
    public RunContext {
        Objects.requireNonNull(runId, "runId");
        parentRunId = Objects.requireNonNull(parentRunId, "parentRunId");
        correlationAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(correlationAttributes, "correlationAttributes")));
        if (parentRunId.filter(runId::equals).isPresent()) {
            throw new IllegalArgumentException("a run cannot be its own parent");
        }
    }
}
