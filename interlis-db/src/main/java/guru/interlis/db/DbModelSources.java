package guru.interlis.db;

import guru.interlis.compiler.AnalysisReuse;
import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.ResolutionFingerprint;
import guru.interlis.compiler.ResolutionProblem;
import guru.interlis.compiler.ResolutionReason;
import guru.interlis.compiler.SourceSet;
import guru.interlis.compiler.TranslationBinding;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.DiagnosticLanguage;
import guru.interlis.diagnostics.DiagnosticMessageFormatter;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Supplies explicit local sources and import bindings to the compiler. */
final class DbModelSources {
    private DbModelSources() { }

    record Compiled(TransferPlan plan, List<SyntaxSnapshot> syntax) { }

    static Compiled compile(List<String> models, Collection<SourceText> input, String description,
                                CancellationToken token) {
        return compile(models, input, description, false, token);
    }

    static Compiled compile(List<String> models, Collection<SourceText> input, String description,
                                boolean originalWireModels, CancellationToken token) {
        var parser = SyntaxServices.create();
        var sources = new LinkedHashMap<SourceId, SourceText>();
        var syntax = new LinkedHashMap<SourceId, SyntaxSnapshot>();
        var locations = new LinkedHashMap<String, List<ModelLocation>>();
        for (var source : input) {
            token.throwIfCancellationRequested();
            sources.put(source.id(), source);
            var parsed = parser.parse(source, ParseOptions.defaults(ParseMode.EDITOR), token);
            syntax.put(source.id(), parsed);
            for (var model : parsed.root().models()) {
                var location = new ModelLocation(source.id(), source.revision(), model.name(),
                        Optional.of(model.modelVersion()), model.range().startOffset());
                locations.computeIfAbsent(model.name(), ignored -> new ArrayList<>()).add(location);
            }
        }
        var imports = new ArrayList<ImportBinding>();
        var translations = new ArrayList<TranslationBinding>();
        var originalModels24 = new LinkedHashMap<String, String>();
        for (var parsed : syntax.values()) {
            for (var importer : parsed.root().models()) {
                token.throwIfCancellationRequested();
                importer.translationSource().ifPresent(original -> {
                    var candidates = locations.getOrDefault(original.text(), List.of()).stream()
                            .filter(candidate -> importer.translationVersion().isEmpty()
                                    || importer.translationVersion().equals(candidate.modelVersion())).toList();
                    if (candidates.size() == 1) {
                        var source = parsed.source();
                        var translated = new ModelLocation(source.id(), source.revision(), importer.name(),
                                Optional.of(importer.modelVersion()), importer.range().startOffset());
                        translations.add(new TranslationBinding(translated, candidates.getFirst()));
                        if (importer.languageVersion() == LanguageVersion.ILI24) {
                            originalModels24.put(importer.name(), original.text());
                        }
                    }
                });
                for (var imported : importer.imports()) {
                    String name = imported.modelName();
                    if ("INTERLIS".equals(name)) continue;
                    var targets = locations.getOrDefault(name, List.of());
                    var site = new ImportSite(imported.range(), importer.name(), name);
                    if (targets.size() == 1) {
                        imports.add(new ImportBinding(site, new ImportResolution.Resolved(targets.getFirst())));
                    } else if (!targets.isEmpty()) {
                        var problem = new ResolutionProblem(new DiagnosticId("ambiguous-import:" + site.nameRange()),
                                ResolutionReason.AMBIGUOUS, name);
                        imports.add(new ImportBinding(site, new ImportResolution.Ambiguous(targets, problem)));
                    }
                }
            }
        }
        var selected = new LinkedHashSet<>(models);
        if (originalWireModels) {
            for (String model : models) {
                for (String original = originalModels24.get(model); original != null && selected.add(original);
                     original = originalModels24.get(original)) { }
            }
        }
        var selectors = selected.stream().map(name -> (ModelSelector) new ModelSelector.ByName(name, Optional.empty(),
                Optional.empty())).toList();
        var sourceSet = SourceSet.of(sources, imports, List.of(), ResolutionFingerprint.none(), translations);
        var request = new CompilationRequest(selectors, sourceSet, CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, new AnalysisReuse(Optional.empty(), syntax), token);
        String target = description + " for " + String.join(", ", models);
        var formatter = new DiagnosticMessageFormatter();
        var schema = switch (outcome) {
            case CompilationOutcome.Succeeded succeeded -> succeeded.schema();
            case CompilationOutcome.Rejected rejected -> throw new DbException("Cannot compile " + target + " ("
                    + rejected.analysis().diagnostics().stream().filter(diagnostic -> diagnostic.severity() == Severity.ERROR)
                    .map(diagnostic -> diagnostic.code() + " " + formatter.format(diagnostic, DiagnosticLanguage.EN).text())
                    .distinct().limit(5).collect(Collectors.joining(" ")) + ")");
            case CompilationOutcome.Cancelled cancelled -> throw new OperationCancelledException(cancelled.reason());
            case CompilationOutcome.Failed failed -> throw new DbException(
                    "Compiler failed for " + target + " (" + failed.failureId() + ")");
        };
        return switch (TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)) {
            case TransferPlanningResult.Ready ready -> new Compiled(ready.plan(), List.copyOf(syntax.values()));
            case TransferPlanningResult.Unsupported rejected -> throw DbMapping.unsupported(rejected.issues().getFirst().toString());
        };
    }
}
