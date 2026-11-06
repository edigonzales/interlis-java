package guru.interlis.transfer;

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
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class TransferTestSchemas {
    private static final CancellationToken TOKEN = CancellationToken.none();

    private TransferTestSchemas() {}

    static CompiledSchema survey() throws IOException {
        SourceText base = fixture("models/base24.ili");
        SourceText survey = fixture("models/survey24.ili");
        var parser = SyntaxServices.create();
        var options = ParseOptions.defaults(ParseMode.EDITOR);
        var importer = parser.parse(survey, options, TOKEN).root().models().getFirst();
        var target = parser.parse(base, options, TOKEN).root().models().getFirst();
        var site = new ImportSite(importer.imports().getFirst().range(), "Survey", "Base");
        var location = new ModelLocation(base.id(), base.revision(), "Base",
                Optional.of(target.modelVersion()), target.range().startOffset());
        var binding = new ImportBinding(site, new ImportResolution.Resolved(location));
        var sources = SourceSet.of(Map.of(base.id(), base, survey.id(), survey),
                List.of(binding), List.of(), ResolutionFingerprint.none());
        return compile("Survey", sources);
    }

    static SourceText fixture(String relativePath) throws IOException {
        var path = Path.of(System.getProperty("testdata.dir")).resolve(relativePath);
        return new SourceText(new SourceId(path.toUri().toString()), new SourceRevision(1), Files.readString(path));
    }

    static CompiledSchema compileInline(String model, String text) {
        return compile(model, new SourceText(new SourceId("memory:/" + model + ".ili"),
                new SourceRevision(1), text));
    }

    static CompiledSchema compile(String model, SourceText source) {
        return compile(model, SourceSet.of(Map.of(source.id(), source)));
    }

    static SymbolRef ref(CompiledSchema schema, String... path) {
        var refs = schema.queries().findQualifiedName(QualifiedName.of(path));
        assertEquals(1, refs.size(), () -> "Expected one symbol for " + QualifiedName.of(path));
        return refs.getFirst();
    }

    private static CompiledSchema compile(String model, SourceSet sources) {
        var request = new CompilationRequest(
                List.of(new ModelSelector.ByName(model, Optional.empty(), Optional.empty())),
                sources, CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().requestDiagnostics().toString() : outcome.toString()).schema();
    }
}
