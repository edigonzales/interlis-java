package guru.interlis.compiler.internal;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.CompilationFingerprint;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;

import java.util.Comparator;
import java.util.List;

/** Canonical identity of the actual request, independent of a claimed resolver hash. */
public final class CompilationFingerprinter {
    public CompilationFingerprint fingerprint(CompilationRequest request, AnalysisReuse reuse) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "compiler");
        append(canonical, Fingerprints.COMPILER_VERSION);
        append(canonical, "grammar");
        append(canonical, Fingerprints.GRAMMAR_VERSION);
        append(canonical, "options");
        append(canonical, request.options().fingerprint());
        append(canonical, "reuse");
        append(canonical, reuse.previousAnalysis().map(a -> a.snapshotId().value()).orElse("none"));
        for (ModelSelector root : request.roots()) {
            append(canonical, root.toString());
        }
        List<SourceId> sourceIds = request.sources().sources().keySet().stream()
                .sorted(Comparator.comparing(SourceId::value)).toList();
        for (SourceId sourceId : sourceIds) {
            SourceText text = request.sources().sources().get(sourceId);
            append(canonical, sourceId.value());
            append(canonical, Long.toString(text.revision().value()));
            append(canonical, text.contentFingerprint().algorithm());
            append(canonical, text.contentFingerprint().value());
        }
        for (ImportBinding binding : request.sources().imports()) {
            append(canonical, binding.site().toString());
            switch (binding.resolution()) {
                case ImportResolution.Resolved resolved -> append(canonical,
                        "resolved:" + resolved.target());
                case ImportResolution.Missing missing -> append(canonical,
                        "missing:" + missing.problem().reason());
                case ImportResolution.Ambiguous ambiguous -> append(canonical,
                        "ambiguous:" + ambiguous.candidates());
                case ImportResolution.Blocked blocked -> append(canonical,
                        "blocked:" + blocked.problem().reason());
            }
        }
        append(canonical, "resolver");
        append(canonical, request.sources().fingerprint().value());
        return new CompilationFingerprint("SHA-256/compilation-v1",
                Fingerprints.sha256(canonical.toString()));
    }

    private static void append(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }
}
