package guru.interlis.compiler.gui;

import guru.interlis.compiler.*;
import guru.interlis.diagnostics.*;
import guru.interlis.execution.*;
import guru.interlis.model.*;
import guru.interlis.projection.*;
import guru.interlis.source.*;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.workspace.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Loads one immutable revision of the selected local inputs and calls the SDK gate once. */
final class DesktopCompiler {
    @FunctionalInterface interface Loader { String load() throws IOException; }
    record Input(SourceId id, String label, Loader loader) {
        static Input file(Path file) {
            Path normalized = file.toAbsolutePath().normalize();
            return new Input(new SourceId(normalized.toUri().toString()), normalized.toString(), () -> Files.readString(normalized));
        }
        static Input example(String name) {
            return new Input(new SourceId("resource:/" + name), name, () -> {
                try (var resource = DesktopCompiler.class.getResourceAsStream("web/" + name)) {
                    if (resource == null) throw new IOException("Missing bundled model " + name);
                    return new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            });
        }
        @Override public String toString() { return label; }
    }
    record Problem(String severity, String position, String code, String message) { }
    record Dependency(String name, List<Dependency> imports) {
        Dependency { imports = List.copyOf(imports); }
    }
    record Result(String status, boolean accepted, List<Problem> problems, List<Dependency> dependencies, Optional<String> diagram) {
        Result { problems = List.copyOf(problems); dependencies = List.copyOf(dependencies); }
    }
    private final CompilerEngine engine;
    private final DiagnosticMessageFormatter formatter = new DiagnosticMessageFormatter();

    DesktopCompiler(CompilerEngine engine) { this.engine = Objects.requireNonNull(engine); }

    /** Called on a worker. No live JavaFX state is read or written. */
    Result compile(List<Input> inputs, CancellationToken cancellation) throws IOException {
        var updates = new ArrayList<SourceUpdate>();
        var labels = new LinkedHashMap<SourceId, String>();
        for (Input input : inputs) {
            cancellation.throwIfCancellationRequested();
            updates.add(new SourceUpdate(input.id(), new SourceExpectation.Absent(), new SourceChange.Replace(input.loader().load())));
            labels.put(input.id(), input.label());
        }
        List<ModelSelector> roots = labels.keySet().stream().<ModelSelector>map(ModelSelector.BySource::new).toList();
        CompilationOutcome outcome;
        // The standard workspace resolver binds imports/translation originals within the selected local files.
        try (var workspace = Workspaces.create(engine, WorkspaceConfiguration.defaults())) {
            var receipt = workspace.applyBatch(updates, workspace.currentRevision());
            outcome = workspace.compile(new WorkspaceCompileRequest(receipt.workspaceRevision(), roots), cancellation);
        }
        return switch (outcome) {
            case CompilationOutcome.Succeeded success -> {
                var schema = success.schema();
                var diagnostics = problems(success.analysis().requestDiagnostics(), success.analysis().syntaxSnapshots(), labels);
                var dependencies = dependencies(schema.semanticGraph(), schema.rootModels().stream().map(SymbolRef::symbol).toList());
                var projection = DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),
                        DiagramOptions.defaults(new DiagramSelection(schema.rootModels())), cancellation);
                if (projection instanceof DiagramProjectionOutcome.Produced produced)
                    yield new Result("Compiled successfully", true, diagnostics, dependencies,
                            Optional.of(new DiagramJsonCodec().encode(produced.document())));
                yield new Result("Compiled; diagram unavailable: " + ((DiagramProjectionOutcome.Rejected) projection).reasons(),
                        true, diagnostics, dependencies, Optional.empty());
            }
            case CompilationOutcome.Rejected rejected -> {
                var graph = rejected.analysis().semanticGraph();
                var selected = graph.symbols().stream().filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast)
                        .filter(model -> model.header().nameRange().map(range -> labels.containsKey(range.source())).orElse(false))
                        .map(model -> model.header().id()).toList();
                yield new Result("Compilation rejected: " + rejected.rejection().reasons(), false,
                        problems(rejected.analysis().requestDiagnostics(), rejected.analysis().syntaxSnapshots(), labels), dependencies(graph, selected), Optional.empty());
            }
            case CompilationOutcome.Cancelled cancelled -> new Result("Compilation cancelled: " + cancelled.reason(), false, List.of(), List.of(), Optional.empty());
            case CompilationOutcome.Failed failed -> new Result("Compiler failed: " + failed.failureId(), false,
                    problems(List.of(failed.error()), Map.of(), labels), List.of(), Optional.empty());
        };
    }

    private List<Problem> problems(List<Diagnostic> diagnostics, Map<SourceId, SyntaxSnapshot> sources, Map<SourceId, String> labels) {
        return diagnostics.stream().map(diagnostic -> new Problem(diagnostic.severity().name(), diagnostic.primaryRange().map(range -> {
            SyntaxSnapshot syntax = sources.get(range.source());
            if (syntax == null || !range.revision().equals(syntax.source().revision())) return range.source().value();
            SourceText source = syntax.source();
            var position = source.lineMap().positionAt(range.startOffset());
            return labels.get(range.source()) + ":" + (position.line() + 1) + ":" + (position.character() + 1);
        }).orElse("—"), diagnostic.code().value(), formatter.format(diagnostic, DiagnosticLanguage.EN).text())).toList();
    }

    private static List<Dependency> dependencies(SemanticGraph graph, List<SymbolId> roots) {
        var expanded = new HashSet<SymbolId>();
        return roots.stream().flatMap(root -> dependency(graph, root, expanded, 0).stream()).toList();
    }
    private static Optional<Dependency> dependency(SemanticGraph graph, SymbolId id, Set<SymbolId> expanded, int depth) {
        if (!(graph.symbol(id).orElse(null) instanceof ModelSymbol model)) return Optional.empty();
        String name = model.header().name().value();
        if (!expanded.add(id)) return Optional.of(new Dependency(name + " (already listed)", List.of()));
        if (depth == 64) return Optional.of(new Dependency(name + " (depth limit)", List.of()));
        var targets = Stream.concat(model.imports().stream().flatMap(value -> value.targetModel().stream()),
                model.translation().flatMap(ModelTranslation::sourceTarget).stream());
        return Optional.of(new Dependency(name, targets.distinct().flatMap(target -> dependency(graph, target, expanded, depth + 1).stream()).toList()));
    }
}
