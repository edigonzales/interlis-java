package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.source.SourceId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One canonical request selection, shared by the published snapshot and gate. */
final class RequestDiagnosticSelector {
    private static final Set<String> CONFIGURATION_CODES = Set.of(
            "ILI-SEM-0011", "ILI-RES-0005", "ILI-RES-0006", "ILI-RES-0007");

    private RequestDiagnosticSelector() { }

    /** Retains original values/order. Unlocated input/configuration problems are conservative globals. */
    static List<Diagnostic> select(List<Diagnostic> all, Closure closure, RootSelection roots,
                                   Set<SourceId> syntaxSources,
                                   CancellationToken cancellation) {
        Set<DiagnosticId> explicit = new HashSet<>(roots.diagnostics());
        explicit.addAll(closure.diagnostics());
        var selected = new ArrayList<Diagnostic>();
        for (Diagnostic diagnostic : all) {
            cancellation.throwIfCancellationRequested();
            boolean relevant = explicit.contains(diagnostic.id())
                    || CONFIGURATION_CODES.contains(diagnostic.code().value())
                    || diagnostic.primaryRange().map(range -> {
                        // A damaged required file cannot be proved syntactically valid by model ranges.
                        boolean syntaxError = diagnostic.severity() == Severity.ERROR
                                && (diagnostic.phase() == Phase.LEXING || diagnostic.phase() == Phase.PARSING);
                        return syntaxError ? syntaxSources.contains(range.source()) : closure.contains(range);
                    }).orElse(true);
            if (relevant) selected.add(diagnostic);
        }
        return List.copyOf(selected);
    }

    /** Keeps closure order followed by explicitly associated sources in request order. */
    static Set<SourceId> syntaxSources(Closure closure, CompilationRequest request, CancellationToken cancellation) {
        var sources = new LinkedHashSet<>(closure.sources());
        // An explicit source remains requested even if parser recovery yields no model.
        for (var selector : request.roots()) {
            cancellation.throwIfCancellationRequested();
            switch (selector) {
                case ModelSelector.BySource source -> sources.add(source.source());
                case ModelSelector.ByName name -> name.sourceHint().ifPresent(sources::add);
            }
        }
        return sources;
    }
}
