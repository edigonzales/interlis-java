package guru.interlis.compiler.internal;

import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;

import java.util.Objects;

/** Verifies the structural SourceSet contract before semantic use. */
public final class SourceSetVerifier {
    public void verify(SourceSet sourceSet, DiagnosticCollector collector) {
        Objects.requireNonNull(sourceSet, "sourceSet");
        for (var entry : sourceSet.sources().entrySet()) {
            SourceText text = entry.getValue();
            if (!entry.getKey().equals(text.id())) {
                throw new IllegalArgumentException("SourceSet key does not match SourceText.id()");
            }
        }
        for (ImportBinding binding : sourceSet.imports()) {
            if (binding.resolution() instanceof ImportResolution.Resolved resolved) {
                SourceText target = sourceSet.sources().get(resolved.target().source());
                if (target == null
                        || !target.revision().equals(resolved.target().revision())) {
                    collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-RES-0004"),
                                    Severity.ERROR, Phase.SOURCE_RESOLUTION,
                                    "diagnostic.resolution.identity")
                            .at(binding.site().nameRange())
                            .argument(new DiagnosticArgument.IdentifierArgument("expected",
                                    binding.site().importedModel()))
                            .argument(new DiagnosticArgument.IdentifierListArgument("actual",
                                    java.util.List.of("binding points to a missing revision"))));
                }
            }
        }
    }
}
