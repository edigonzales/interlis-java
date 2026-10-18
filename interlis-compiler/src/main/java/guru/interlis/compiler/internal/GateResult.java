package guru.interlis.compiler.internal;

import guru.interlis.compiler.RejectionSummary;
import guru.interlis.model.CompiledSchema;

import java.util.Objects;
import java.util.Optional;

/** Exactly one of accepted schema or rejection summary. */
record GateResult(Optional<CompiledSchema> schema, Optional<RejectionSummary> rejection) {
    GateResult {
        schema = Objects.requireNonNull(schema, "schema");
        rejection = Objects.requireNonNull(rejection, "rejection");
        if (schema.isPresent() == rejection.isPresent()) {
            throw new IllegalArgumentException("gate result needs exactly one outcome");
        }
    }

    static GateResult accepted(CompiledSchema schema) {
        return new GateResult(Optional.of(schema), Optional.empty());
    }

    static GateResult denied(RejectionSummary summary) {
        return new GateResult(Optional.empty(), Optional.of(summary));
    }
}
