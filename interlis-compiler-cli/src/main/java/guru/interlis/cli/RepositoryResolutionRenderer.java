package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.repository.*;
import java.nio.file.Path;
import java.util.*;

import static guru.interlis.cli.ConsoleDiagnosticRenderer.delimitedLocation;

/** Pure presentation of current repository observations; never performs IO or model selection. */
public final class RepositoryResolutionRenderer {
    /** Renders the start of a local directory scan; the path is escaped as diagnostic text. */
    String renderLocalSearch(Path directory, DiagnosticLanguage language, boolean ansiEnabled) {
        return renderInfo("ILI-RES-0015", "diagnostic.resolution.localSearch",
                List.of(new DiagnosticArgument.TextArgument("directory", delimitedLocation(directory.toString()))), language, ansiEnabled);
    }

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
                                lookup ? delimitedLocation(step.repository().toString()) : selectedDetail(step.detail())));
            }
            case RepositoryResolutionObservation.VerificationStarted ignored -> {
                code = "ILI-RES-0013";
                key = "diagnostic.resolution.verification";
                arguments = List.of();
            }
        }
        return Optional.of(renderInfo(code, key, arguments, language, ansiEnabled));
    }

    private static String selectedDetail(String detail) {
        // RepositorySearch stores the version followed by a space and the URI, whose spaces are encoded.
        int separator = detail.lastIndexOf(' ');
        if (separator < 0 || separator == detail.length() - 1) return detail;
        return detail.substring(0, separator + 1) + delimitedLocation(detail.substring(separator + 1));
    }

    private String renderInfo(String code, String key, List<DiagnosticArgument> arguments,
                              DiagnosticLanguage language, boolean ansiEnabled) {
        var diagnosticCode = new DiagnosticCode(code);
        var diagnostic = new Diagnostic(DiagnosticIds.create(diagnosticCode, Phase.SOURCE_RESOLUTION, Optional.empty(), key, arguments),
                diagnosticCode, Severity.INFO, Phase.SOURCE_RESOLUTION, key, arguments, Optional.empty(), List.of(),
                Optional.empty(), List.of(), Optional.empty(), Optional.empty(), List.of());
        return new ConsoleDiagnosticRenderer().render(diagnostic, Map.of(), language,
                new RenderOptions(ansiEnabled, 0, 0, 160, false));
    }
}
