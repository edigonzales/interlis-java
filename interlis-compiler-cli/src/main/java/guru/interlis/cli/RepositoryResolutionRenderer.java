package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.repository.*;
import java.util.*;

/** Pure presentation of current repository observations; never performs IO or model selection. */
public final class RepositoryResolutionRenderer {
    /**
     * Renders a non-null observation using the common language/style policy. Search
     * warnings are left to their existing diagnostic owner, preventing duplicates.
     * Temporary INFO values never enter compiler results, snapshots or gate decisions.
     */
    public Optional<String> render(RepositoryResolutionObservation observation, DiagnosticLanguage language,
                                   boolean ansiEnabled) {
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(language, "language");
        String code;
        String key;
        List<DiagnosticArgument> arguments;
        switch (observation) {
            case RepositoryResolutionObservation.SearchStep current -> {
                var step = current.step();
                if (step.warning()) return Optional.empty();
                boolean lookup = step.kind() == RepositorySearchStep.Kind.LOOKUP;
                code = lookup ? "ILI-RES-0011" : "ILI-RES-0012";
                key = lookup ? "diagnostic.resolution.lookup" : "diagnostic.resolution.selected";
                arguments = List.of(new DiagnosticArgument.IdentifierArgument("model", step.modelName()),
                        new DiagnosticArgument.TextArgument(lookup ? "repository" : "detail",
                                lookup ? step.repository().toString() : step.detail()));
            }
            case RepositoryResolutionObservation.VerificationStarted ignored -> {
                code = "ILI-RES-0013";
                key = "diagnostic.resolution.verification";
                arguments = List.of();
            }
        }
        var diagnosticCode = new DiagnosticCode(code);
        var diagnostic = new Diagnostic(DiagnosticIds.create(diagnosticCode, Phase.SOURCE_RESOLUTION, Optional.empty(), key, arguments),
                diagnosticCode, Severity.INFO, Phase.SOURCE_RESOLUTION, key, arguments, Optional.empty(), List.of(),
                Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
        return Optional.of(new ConsoleDiagnosticRenderer().render(diagnostic, Map.of(), language,
                new RenderOptions(ansiEnabled, 0, 0, 160, false)));
    }
}
