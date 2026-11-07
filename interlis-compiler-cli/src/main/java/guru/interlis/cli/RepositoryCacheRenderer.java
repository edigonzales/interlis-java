package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.repository.*;
import java.util.*;

/**
 * Pure host presentation. The temporary diagnostic value is never published in
 * compiler results, snapshots or the gate. Actual repeated accesses are rendered
 * independently, even if their display identities match. No source text is loaded.
 */
public final class RepositoryCacheRenderer {
    /** Existing WRITE_UNAVAILABLE warning is rendered by its owner, not duplicated as INFO here. */
    public Optional<String> render(RepositoryCacheObservation observation, DiagnosticLanguage language, boolean ansiEnabled) {
        Objects.requireNonNull(observation,"observation"); Objects.requireNonNull(language,"language");
        if (observation.event()==RepositoryCacheEvent.WRITE_UNAVAILABLE) return Optional.empty();
        var code=new DiagnosticCode("ILI-RES-0010");
        var key="diagnostic.resolution.cache";
        List<DiagnosticArgument> arguments=List.of(new DiagnosticArgument.TextArgument("event",observation.event().name()),
                new DiagnosticArgument.TextArgument("source", observation.safeSource()
                        .map(ConsoleDiagnosticRenderer::delimitedLocation).orElse("?")));
        var diagnostic=new Diagnostic(DiagnosticIds.create(code,Phase.SOURCE_RESOLUTION,Optional.empty(),key,arguments),
                code,Severity.INFO,Phase.SOURCE_RESOLUTION,key,arguments,Optional.empty(),List.of(),Optional.empty(),
                List.of(),Optional.empty(),Optional.empty(),List.of());
        return Optional.of(new ConsoleDiagnosticRenderer().render(diagnostic,Map.of(),language,
                new RenderOptions(ansiEnabled,0,0,160,false)));
    }
}
