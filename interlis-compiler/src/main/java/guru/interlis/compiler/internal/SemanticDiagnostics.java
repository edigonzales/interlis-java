package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.RelatedLocation;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.Origin;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.syntax.NameSyntax;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Creates and records semantic drafts in the compilation run’s existing collector. */
final class SemanticDiagnostics {
    private final DiagnosticCollector collector;
    private final Closure closure;
    private final List<Diagnostic> syntaxErrors;
    private final Map<SourceId, Set<String>> unsupportedBySource = new LinkedHashMap<>();

    SemanticDiagnostics(DiagnosticCollector collector, Closure closure) {
        this.collector = Objects.requireNonNull(collector, "collector");
        this.closure = closure;
        syntaxErrors = collector.snapshot().stream().filter(d -> d.severity() == Severity.ERROR
                && (d.phase() == Phase.LEXING || d.phase() == Phase.PARSING)).toList();
    }

    List<Diagnostic> syntaxErrors() { return syntaxErrors; }

    List<Diagnostic> snapshot() { return collector.snapshot(); }

    void importAll(List<Diagnostic> drafts) { collector.importAll(drafts); }

    DiagnosticId emit(DiagnosticDraft draft) { return collector.emit(draft); }

    void registerUnsupportedSource(SourceId source) {
        unsupportedBySource.computeIfAbsent(source, ignored -> new LinkedHashSet<>());
    }

    boolean recordUnsupported(SourceId source, String feature) {
        return unsupportedBySource.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(feature);
    }

    Map<SourceId, Set<String>> unsupportedBySource() {
        var snapshot = new LinkedHashMap<SourceId, Set<String>>();
        unsupportedBySource.forEach((source, features) ->
                snapshot.put(source, Collections.unmodifiableSet(new LinkedHashSet<>(features))));
        return Map.copyOf(snapshot);
    }

    DiagnosticId unresolved(NameSyntax name) {
        var draft = DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0001"),
                        Severity.ERROR, Phase.BINDING, "diagnostic.semantics.unresolved")
                .at(name.range())
                .argument(new DiagnosticArgument.IdentifierArgument("name", name.text()));
        // Only qualified references to an actually failed import are dependent.
        // An unrelated unresolved name in the same file remains independently visible.
        if (name.segments().size() > 1) {
            String qualifier = name.segments().getFirst().text();
            closure.models().stream().filter(m -> m.sourceId().equals(name.range().source())
                    && m.syntax().range().startOffset() <= name.range().startOffset()
                    && m.syntax().range().endOffset() >= name.range().endOffset())
                    .flatMap(m -> m.syntax().imports().stream()).filter(i -> i.modelName().equals(qualifier))
                    .map(i -> collector.importFailure(i.range())).flatMap(Optional::stream).findFirst()
                    .ifPresent(id -> { draft.cause(id); draft.dependsOn(id); });
        }
        return collector.emit(draft);
    }

    DiagnosticId emitUnsupported(SourceId source, SourceRange range, String feature,
                                         String construct) {
        DiagnosticDraft draft = DiagnosticDraft.of(new DiagnosticCode("ILI-IMPL-0001"),
                        Severity.ERROR, Phase.SEMANTIC_CHECKING,
                        "diagnostic.implementation.unsupported")
                .argument(new DiagnosticArgument.TextArgument("feature", feature))
                .argument(new DiagnosticArgument.TextArgument("construct",
                        construct == null ? feature : construct));
        if (range != null) {
            draft.at(range);
        }
        recordUnsupported(source, feature);
        return collector.emit(draft);
    }

    DiagnosticId emitTypeMismatch(SourceId source, SourceRange range, String actual, String expected) {
        return collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0005"), Severity.ERROR,
                        Phase.TYPE_CHECKING, "diagnostic.semantics.type")
                .at(range)
                .argument(new DiagnosticArgument.TypeNameArgument("actual", actual))
                .argument(new DiagnosticArgument.TypeNameArgument("expected", expected)));
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason) {
        return emitRule(decl, rule, reason, ruleRange(decl));
    }

    private SourceRange ruleRange(DeclInfo decl) {
        return decl.origin() instanceof Origin.DeclaredOrigin declared
                ? declared.range() : decl.nameRange().orElse(null);
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason, SourceRange range) {
        return emitRule(decl, rule, reason, range, List.of());
    }

    DiagnosticId emitRule(DeclInfo decl, String rule, String reason, List<RelatedLocation> related) {
        return emitRule(decl, rule, reason, ruleRange(decl), related);
    }

    private DiagnosticId emitRule(DeclInfo decl, String rule, String reason, SourceRange range,
                                  List<RelatedLocation> related) {
        var draft = DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0010"),
                        Severity.ERROR, Phase.SEMANTIC_CHECKING,
                        "diagnostic.semantics.inheritance")
                .at(range)
                .argument(new DiagnosticArgument.IdentifierArgument("element", decl.name()))
                .argument(new DiagnosticArgument.TextArgument("rule", rule + "/" + reason));
        related.forEach(draft::related);
        return collector.emit(draft);
    }
}
