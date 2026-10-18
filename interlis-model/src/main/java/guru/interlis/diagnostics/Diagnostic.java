package guru.interlis.diagnostics;

import guru.interlis.execution.FailureId;
import guru.interlis.model.SymbolId;
import guru.interlis.source.SourceRange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable structured diagnostic. The code and typed arguments are its
 * identity; localized message text is derived later.
 */
public record Diagnostic(
        DiagnosticId id,
        DiagnosticCode code,
        Severity severity,
        Phase phase,
        String messageKey,
        List<DiagnosticArgument> arguments,
        Optional<SourceRange> primaryRange,
        List<RelatedLocation> relatedLocations,
        Optional<DiagnosticId> causeId,
        List<DiagnosticId> dependsOn,
        Optional<SymbolId> symbolId,
        Optional<FailureId> failureId,
        List<DiagnosticTag> tags) {

    /** Creates a validated instance. */
    public Diagnostic {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(messageKey, "messageKey");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        primaryRange = Objects.requireNonNull(primaryRange, "primaryRange");
        relatedLocations = List.copyOf(Objects.requireNonNull(relatedLocations, "relatedLocations"));
        causeId = Objects.requireNonNull(causeId, "causeId");
        dependsOn = List.copyOf(Objects.requireNonNull(dependsOn, "dependsOn"));
        symbolId = Objects.requireNonNull(symbolId, "symbolId");
        failureId = Objects.requireNonNull(failureId, "failureId");
        tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
    }
}
