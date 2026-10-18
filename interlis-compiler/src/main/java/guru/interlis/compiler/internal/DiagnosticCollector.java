package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCatalog;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticIds;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Run-scoped canonical diagnostic collector. It deduplicates exact repeats,
 * sorts canonically on freeze and stops expansion at the configured limit.
 */
public final class DiagnosticCollector {
    private final DiagnosticCatalog catalog;
    private final int maxDiagnostics;
    private final Map<DiagnosticId, Diagnostic> diagnostics = new LinkedHashMap<>();
    private boolean closed;
    private boolean budgetExceeded;

    public DiagnosticCollector(DiagnosticCatalog catalog, int maxDiagnostics) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (maxDiagnostics <= 0) {
            throw new IllegalArgumentException("maxDiagnostics must be positive");
        }
        this.maxDiagnostics = maxDiagnostics;
    }

    public DiagnosticId emit(DiagnosticDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (closed) {
            throw new IllegalStateException("diagnostic collector is closed");
        }
        catalog.validate(draft);
        DiagnosticId id = DiagnosticIds.create(draft.code(), draft.phase(),
                draft.primaryRange(), draft.messageKey(), draft.arguments());
        if (diagnostics.containsKey(id)) {
            return id;
        }
        if (diagnostics.size() >= maxDiagnostics) {
            budgetExceeded = true;
            throw new AnalysisBudgetExceededException("maxDiagnostics", maxDiagnostics);
        }
        Diagnostic diagnostic = new Diagnostic(id, draft.code(), draft.severity(),
                draft.phase(), draft.messageKey(), draft.arguments(), draft.primaryRange(),
                draft.relatedLocations(), draft.causeId(), draft.dependsOn(),
                draft.symbolId(), draft.failureId(), draft.tags());
        diagnostics.put(id, diagnostic);
        return id;
    }

    public Map<DiagnosticId, DiagnosticId> importAll(List<Diagnostic> imported) {
        Objects.requireNonNull(imported, "imported");
        Map<DiagnosticId, DiagnosticId> mapping = new LinkedHashMap<>();
        if (imported.isEmpty()) {
            return mapping;
        }
        if (closed) {
            throw new IllegalStateException("diagnostic collector is closed");
        }
        if ((long) diagnostics.size() + imported.size() > maxDiagnostics) {
            budgetExceeded = true;
            throw new AnalysisBudgetExceededException("maxDiagnostics", maxDiagnostics);
        }
        // First pass: establish new ids for all imported diagnostics.
        List<Diagnostic> pending = new ArrayList<>();
        for (Diagnostic diagnostic : imported) {
            DiagnosticId newId = DiagnosticIds.create(diagnostic.code(), diagnostic.phase(),
                    diagnostic.primaryRange(), diagnostic.messageKey(), diagnostic.arguments());
            mapping.put(diagnostic.id(), newId);
            pending.add(diagnostic);
        }
        for (Diagnostic diagnostic : pending) {
            DiagnosticId newId = mapping.get(diagnostic.id());
            Optional<DiagnosticId> cause = diagnostic.causeId()
                    .map(mapping::get)
                    .filter(Objects::nonNull);
            List<DiagnosticId> dependsOn = diagnostic.dependsOn().stream()
                    .map(mapping::get)
                    .filter(Objects::nonNull)
                    .toList();
            Diagnostic rewritten = new Diagnostic(newId, diagnostic.code(),
                    diagnostic.severity(), diagnostic.phase(), diagnostic.messageKey(),
                    diagnostic.arguments(), diagnostic.primaryRange(),
                    diagnostic.relatedLocations(), cause, dependsOn,
                    diagnostic.symbolId(), diagnostic.failureId(), diagnostic.tags());
            diagnostics.putIfAbsent(newId, rewritten);
        }
        return mapping;
    }

    public boolean hasErrors() {
        return diagnostics.values().stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public boolean budgetExceeded() {
        return budgetExceeded;
    }

    public int size() {
        return diagnostics.size();
    }

    public List<Diagnostic> freeze() {
        closed = true;
        List<Diagnostic> result = new ArrayList<>(diagnostics.values());
        result.sort(canonicalOrder());
        return List.copyOf(result);
    }

    public List<Diagnostic> snapshot() {
        List<Diagnostic> result = new ArrayList<>(diagnostics.values());
        result.sort(canonicalOrder());
        return List.copyOf(result);
    }

    private static Comparator<Diagnostic> canonicalOrder() {
        return Comparator
                .comparing((Diagnostic d) -> d.primaryRange().isPresent())
                .thenComparing(d -> d.primaryRange().map(r -> r.source().value()).orElse(""))
                .thenComparingLong(d -> d.primaryRange().map(r -> r.revision().value()).orElse(0L))
                .thenComparingInt(d -> d.primaryRange().map(r -> r.startOffset()).orElse(0))
                .thenComparingInt(d -> d.primaryRange().map(r -> r.endOffset()).orElse(0))
                .thenComparing(Diagnostic::phase)
                .thenComparing(d -> d.code().value())
                .thenComparing(Diagnostic::messageKey)
                .thenComparing(DiagnosticCollector::argumentsKey);
    }

    private static String argumentsKey(Diagnostic d) {
        StringBuilder builder = new StringBuilder();
        for (DiagnosticArgument argument : d.arguments()) {
            builder.append(argument.name()).append('=').append(argument).append(';');
        }
        return builder.toString();
    }

    public static DiagnosticDraft error(DiagnosticCode code, Phase phase, String messageKey) {
        return DiagnosticDraft.of(code, Severity.ERROR, phase, messageKey);
    }
}
