package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxService;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.SyntaxSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CompilerTestSupport {
    private CompilerTestSupport() {
    }

    static SourceText source(String id, String text) {
        return new SourceText(new SourceId(id), new SourceRevision(1), text);
    }

    static SyntaxSnapshot parse(SourceText source) {
        SyntaxService syntax = SyntaxServices.create();
        return syntax.parse(source, ParseOptions.defaults(ParseMode.EDITOR), CancellationToken.none());
    }

    static ImportBinding binding(SyntaxSnapshot importer, String importedModel,
                                 SourceText target, SyntaxSnapshot targetSyntax) {
        var importSyntax = importer.root().models().getFirst().imports().stream()
                .filter(i -> i.modelName().equals(importedModel))
                .findFirst().orElseThrow();
        var targetModel = targetSyntax.root().models().stream()
                .filter(m -> m.name().equals(importedModel))
                .findFirst().orElseThrow();
        ImportSite site = new ImportSite(importSyntax.range(),
                importer.root().models().getFirst().name(), importedModel);
        ModelLocation location = new ModelLocation(target.id(), target.revision(),
                targetModel.name(), Optional.of(targetModel.modelVersion()),
                targetModel.range().startOffset());
        return new ImportBinding(site, new ImportResolution.Resolved(location));
    }

    static CompilationRequest request(List<String> roots, Map<SourceId, SourceText> sources,
                                      List<ImportBinding> bindings, CompilerOptions options) {
        List<ModelSelector> selectors = new ArrayList<>();
        for (String root : roots) {
            selectors.add(new ModelSelector.ByName(root, Optional.empty(), Optional.empty()));
        }
        SourceSet sourceSet = SourceSet.of(new LinkedHashMap<>(sources), bindings, List.of(),
                new ResolutionFingerprint("test-resolver/v1"));
        return new CompilationRequest(selectors, sourceSet, options);
    }

    static CompilationRequest request(SyntaxSnapshot importer, SyntaxSnapshot target,
                                      CompilerOptions options) {
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        sources.put(target.source().id(), target.source());
        sources.put(importer.source().id(), importer.source());
        List<ImportBinding> bindings = List.of(binding(importer, "Base", target.source(), target));
        return request(List.of("Survey"), sources, bindings, options);
    }
}
