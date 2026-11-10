package guru.interlis.xtf24;

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
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class Xtf24TestSchemas {
    private Xtf24TestSchemas() {}

    static TransferPlan plan(String text, String... roots) {
        var source = new SourceText(new SourceId("memory:/names.ili"), new SourceRevision(1), text);
        List<ModelSelector> selectors = Arrays.stream(roots)
                .map(name -> (ModelSelector) new ModelSelector.ByName(name, Optional.empty(), Optional.empty()))
                .toList();
        var syntax = SyntaxServices.create().parse(source, ParseOptions.defaults(ParseMode.EDITOR),
                CancellationToken.none()).root();
        var bindings = syntax.models().stream().flatMap(importer -> importer.imports().stream().map(imported -> {
            var target = syntax.models().stream().filter(model -> model.name().equals(imported.modelName()))
                    .findFirst().orElseThrow();
            var site = new ImportSite(imported.range(), importer.name(), target.name());
            var location = new ModelLocation(source.id(), source.revision(), target.name(),
                    Optional.of(target.modelVersion()), target.range().startOffset());
            return new ImportBinding(site, new ImportResolution.Resolved(location));
        })).toList();
        var sources = SourceSet.of(Map.of(source.id(), source), bindings, List.of(), ResolutionFingerprint.none());
        var request = new CompilationRequest(selectors, sources, CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, outcome::toString).schema();
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)).plan();
    }
}
