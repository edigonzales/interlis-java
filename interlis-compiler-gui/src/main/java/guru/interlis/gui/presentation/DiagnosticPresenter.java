package guru.interlis.gui.presentation;

import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticDisplayPolicy;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.DiagnosticMessage;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.diagnostics.DiagnosticPresentation;
import guru.interlis.diagnostics.Severity;
import guru.interlis.gui.application.CompileResult;
import guru.interlis.repository.RepositorySearchStep;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourcePosition;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Projects only the canonical diagnostics and the source revisions of one completed run. */
public final class DiagnosticPresenter {
    private final DiagnosticMessageFormatter formatter = new DiagnosticMessageFormatter();
    private final DiagnosticLanguage language;
    private final List<Diagnostic> original;
    private final Map<DiagnosticId, Row> rows;
    private final Map<SourceId, CompileResult.SourceSnapshot> sources;

    public record Row(DiagnosticId id, Severity severity, String code, String message,
                      String source, String sourceDetail, Optional<SourcePosition> position) {
    }

    public record Related(String message, SourceRange range, String location, String label) {
        @Override
        public String toString() {
            return label + " · " + message;
        }
    }

    public record Details(Diagnostic diagnostic, DiagnosticMessage message, String location,
                          List<Related> related, String report) {
        public Details {
            related = List.copyOf(related);
        }
    }

    public record Projection(List<Row> visible, int total, int grouped) {
        public Projection {
            visible = List.copyOf(visible);
        }
    }

    public DiagnosticPresenter(CompileResult result, DiagnosticLanguage language) {
        this(result.diagnostics(), result.sources(), language);
    }

    public DiagnosticPresenter(List<Diagnostic> diagnostics, Map<SourceId, CompileResult.SourceSnapshot> sources,
                               DiagnosticLanguage language) {
        original = List.copyOf(diagnostics);
        this.sources = Map.copyOf(sources);
        this.language = Objects.requireNonNull(language);
        var index = new LinkedHashMap<DiagnosticId, Row>();
        for (Diagnostic diagnostic : original) {
            var range = diagnostic.primaryRange();
            index.put(diagnostic.id(), new Row(diagnostic.id(), diagnostic.severity(), diagnostic.code().value(),
                    formatter.format(diagnostic, language).text(),
                    range.map(SourceRange::source).map(DiagnosticPresenter::shortSource).orElse(""),
                    range.map(SourceRange::source).map(DiagnosticPresenter::displaySource).orElse(""),
                    range.flatMap(this::position)));
        }
        rows = Map.copyOf(index);
    }

    /** Filters and groups the view while leaving the original diagnostics and total intact. */
    public Projection present(Set<Severity> severities, String query, boolean groupRelated, Optional<SourceId> sourceFilter) {
        String search = query.strip().toLowerCase(Locale.ROOT);
        var matching = original.stream()
                .filter(diagnostic -> severities.contains(diagnostic.severity()))
                .filter(diagnostic -> sourceFilter.isEmpty() || diagnostic.primaryRange()
                        .filter(range -> sourceFilter.orElseThrow().equals(range.source())).isPresent())
                .filter(diagnostic -> matches(rows.get(diagnostic.id()), search))
                .toList();
        var view = new DiagnosticPresentation().project(matching,
                new DiagnosticDisplayPolicy(groupRelated, Integer.MAX_VALUE));
        return new Projection(view.visibleDiagnostics().stream().map(diagnostic -> rows.get(diagnostic.id())).toList(),
                original.size(), view.hiddenCount());
    }

    public Optional<Details> details(DiagnosticId id) {
        return original.stream().filter(diagnostic -> diagnostic.id().equals(id)).findFirst().map(diagnostic -> {
            var message = formatter.format(diagnostic, language);
            var related = diagnostic.relatedLocations().stream().map(location ->
                    new Related(formatter.formatRelated(location, language), location.range(), location(location.range()),
                            shortSource(location.range().source()) + position(location.range())
                                    .map(pos -> " · " + displayPosition(pos)).orElse(""))).toList();
            String location = diagnostic.primaryRange().map(this::location).orElse("No source location");
            var report = new StringBuilder(diagnostic.severity() + " " + diagnostic.code().value() + " · "
                    + diagnostic.phase() + "\n" + message.text() + "\n" + location);
            message.hint().ifPresent(hint -> report.append("\nHint: ").append(hint));
            related.forEach(item -> report.append("\nRelated: ").append(item.location()).append(" · ").append(item.message()));
            diagnostic.causeId().ifPresent(cause -> report.append("\nCause: ").append(cause.value()));
            diagnostic.dependsOn().forEach(dependency -> report.append("\nDepends on: ").append(dependency.value()));
            diagnostic.failureId().ifPresent(failure -> report.append("\nFailure ID: ").append(failure.value()));
            return new Details(diagnostic, message, location, related, report.toString());
        });
    }

    public List<DiagnosticId> dependents(DiagnosticId cause) {
        return original.stream().filter(diagnostic -> diagnostic.causeId().filter(cause::equals).isPresent()
                        || diagnostic.dependsOn().contains(cause))
                .map(Diagnostic::id).toList();
    }

    public Optional<SourceText> source(SourceRange range) {
        var snapshot = sources.get(range.source());
        return snapshot == null ? Optional.empty() : Optional.of(snapshot.source())
                .filter(text -> text.revision().equals(range.revision()) && range.endOffset() <= text.length());
    }

    private Optional<SourcePosition> position(SourceRange range) {
        return source(range).map(text -> text.lineMap().positionAt(range.startOffset()));
    }

    private String location(SourceRange range) {
        return displaySource(range.source()) + " · revision " + range.revision().value()
                + position(range).map(pos -> " · " + displayPosition(pos)).orElse("")
                + " · UTF-16 " + range.startOffset() + "–" + range.endOffset();
    }

    public static String displaySource(SourceId id) {
        return DiagnosticMessageFormatter.safeText(RepositorySearchStep.safeUri(id.value()));
    }

    public static String shortSource(SourceId id) {
        String value = displaySource(id);
        int separator = value.lastIndexOf('/');
        return separator >= 0 && separator < value.length() - 1 ? value.substring(separator + 1) : value;
    }

    public static String displayPosition(SourcePosition position) {
        return (position.line() + 1) + ":" + (position.character() + 1);
    }

    private static boolean matches(Row row, String search) {
        return (row.code() + " " + row.message() + " " + row.sourceDetail() + " " + row.severity())
                .toLowerCase(Locale.ROOT).contains(search);
    }
}
