package guru.interlis.compiler.internal;

import guru.interlis.compiler.*;
import guru.interlis.diagnostics.*;
import guru.interlis.source.*;
import java.util.*;

/** Canonical field identity of actual input; no record/debug strings or claimed resolver certificate. */
public final class CompilationFingerprinter {
    public CompilationFingerprint fingerprint(CompilationRequest request, AnalysisReuse reuse) {
        var b = new StringBuilder("compilation-v3");
        field(b, Fingerprints.COMPILER_VERSION); field(b, Fingerprints.GRAMMAR_VERSION);
        field(b, request.options().fingerprint());
        count(b, request.roots().size());
        for (var root : request.roots()) switch (root) {
            case ModelSelector.BySource source -> { field(b, "source"); field(b, source.source().value()); }
            case ModelSelector.ByName name -> {
                field(b, "name"); field(b, name.modelName()); optional(b, name.modelVersion());
                optional(b, name.sourceHint().map(SourceId::value));
            }
        }
        var ids = request.sources().sources().keySet().stream().sorted(Comparator.comparing(SourceId::value)).toList();
        count(b, ids.size());
        for (var id : ids) {
            var text = request.sources().sources().get(id);
            field(b, id.value()); field(b, Long.toString(text.revision().value()));
            field(b, text.contentFingerprint().algorithm()); field(b, text.contentFingerprint().value());
        }
        count(b, request.sources().imports().size());
        request.sources().imports().stream().map(this::bindingKey).sorted().forEach(value -> field(b, value));
        count(b, request.sources().translations().size());
        request.sources().translations().stream().map(binding -> {
            var key = new StringBuilder(); location(key, binding.translated()); location(key, binding.original()); return key.toString();
        }).sorted().forEach(value -> field(b, value));
        count(b, request.sources().resolutionDiagnostics().size());
        request.sources().resolutionDiagnostics().forEach(d -> diagnostic(b, d));
        field(b, request.sources().fingerprint().value());
        return new CompilationFingerprint("SHA-256/compilation-v3", Hex.encode(Sha256.digestUtf16(b.toString())));
    }

    private String bindingKey(ImportBinding binding) {
        var b = new StringBuilder(); range(b, binding.site().nameRange());
        field(b, binding.site().importingModel()); field(b, binding.site().importedModel());
        switch (binding.resolution()) {
            case ImportResolution.Resolved resolved -> { field(b, "resolved"); location(b, resolved.target()); }
            case ImportResolution.Missing missing -> { field(b, "missing"); problem(b, missing.problem()); }
            case ImportResolution.Blocked blocked -> { field(b, "blocked"); problem(b, blocked.problem()); }
            case ImportResolution.Ambiguous ambiguous -> {
                field(b, "ambiguous"); count(b, ambiguous.candidates().size());
                ambiguous.candidates().forEach(candidate -> location(b, candidate)); problem(b, ambiguous.problem());
            }
        }
        return b.toString();
    }
    private static void location(StringBuilder b, ModelLocation location) {
        field(b, location.source().value()); field(b, Long.toString(location.revision().value()));
        field(b, location.modelName()); optional(b, location.modelVersion()); count(b, location.declarationOffset());
    }
    private static void range(StringBuilder b, SourceRange range) {
        field(b, range.source().value()); field(b, Long.toString(range.revision().value()));
        count(b, range.startOffset()); count(b, range.endOffset());
    }
    private static void problem(StringBuilder b, ResolutionProblem problem) {
        field(b, problem.diagnosticId().value()); field(b, problem.reason().name()); field(b, problem.safeDetail());
    }
    private static void diagnostic(StringBuilder b, Diagnostic diagnostic) {
        field(b, diagnostic.id().value()); field(b, diagnostic.code().value()); field(b, diagnostic.severity().name());
        field(b, diagnostic.phase().name()); field(b, diagnostic.messageKey()); arguments(b, diagnostic.arguments());
        field(b, Boolean.toString(diagnostic.primaryRange().isPresent())); diagnostic.primaryRange().ifPresent(value -> range(b, value));
        count(b, diagnostic.relatedLocations().size());
        diagnostic.relatedLocations().forEach(related -> { range(b, related.range()); field(b, related.messageKey()); arguments(b, related.arguments()); });
        optional(b, diagnostic.causeId().map(DiagnosticId::value)); count(b, diagnostic.dependsOn().size());
        diagnostic.dependsOn().forEach(value -> field(b, value.value()));
        optional(b, diagnostic.symbolId().map(guru.interlis.model.SymbolId::value));
        optional(b, diagnostic.failureId().map(guru.interlis.execution.FailureId::value));
        count(b, diagnostic.tags().size()); diagnostic.tags().forEach(value -> field(b, value.name()));
    }
    private static void arguments(StringBuilder b, List<DiagnosticArgument> arguments) {
        count(b, arguments.size());
        for (var argument : arguments) {
            field(b, argument.name());
            switch (argument) {
                case DiagnosticArgument.TextArgument value -> { field(b, "text"); field(b, value.value()); }
                case DiagnosticArgument.IdentifierArgument value -> { field(b, "identifier"); field(b, value.value()); }
                case DiagnosticArgument.TokenArgument value -> { field(b, "token"); field(b, value.value()); }
                case DiagnosticArgument.TypeNameArgument value -> { field(b, "type"); field(b, value.value()); }
                case DiagnosticArgument.IntegerArgument value -> { field(b, "integer"); field(b, value.value().toString()); }
                case DiagnosticArgument.DecimalArgument value -> {
                    field(b, "decimal"); field(b, value.value().unscaledValue().toString()); count(b, value.value().scale()); optional(b, value.raw());
                }
                case DiagnosticArgument.IdentifierListArgument value -> {
                    field(b, "identifiers"); count(b, value.values().size()); value.values().forEach(item -> field(b, item));
                }
            }
        }
    }
    private static void count(StringBuilder b, int count) { field(b, Integer.toString(count)); }
    private static void optional(StringBuilder b, Optional<String> value) {
        field(b, Boolean.toString(value.isPresent())); value.ifPresent(text -> field(b, text));
    }
    private static void field(StringBuilder b, String value) { b.append(value.length()).append(':').append(value).append(';'); }
}
