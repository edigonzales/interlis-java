package guru.interlis.compiler.internal;

import guru.interlis.compiler.RejectionSummary;
import guru.interlis.model.CompiledSchema;

import java.util.Objects;
import java.util.Optional;

/** An acceptance or rejection, with a schema only on the accepted compile path. */
record GateResult(Optional<CompiledSchema> schema, Optional<RejectionSummary> rejection) {
    GateResult {
        schema = Objects.requireNonNull(schema, "schema");
        rejection = Objects.requireNonNull(rejection, "rejection");
        if (schema.isPresent() && rejection.isPresent()) {
            throw new IllegalArgumentException("a rejection cannot carry a schema");
        }
    }

    boolean accepted() {
        return rejection.isEmpty();
    }

    static GateResult acceptedAnalysis() {
        return new GateResult(Optional.empty(), Optional.empty());
    }

    static GateResult accepted(CompiledSchema schema) {
        return new GateResult(Optional.of(schema), Optional.empty());
    }

    static GateResult denied(RejectionSummary summary) {
        return new GateResult(Optional.empty(), Optional.of(summary));
    }
}
