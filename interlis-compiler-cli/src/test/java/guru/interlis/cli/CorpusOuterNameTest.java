package guru.interlis.cli;

import com.google.gson.JsonParser;
import guru.interlis.compiler.*;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CorpusOuterNameTest {
    private static final Path DATA = Path.of(System.getProperty("testdata.dir"));
    private static final SourceId SOURCE = new SourceId("memory:/corpus.ili");

    @TempDir Path folder;

    @Test void originalMetadataRejectsOuterNameAndRenamedClassInheritsMembers() throws Exception {
        String model = "SO_AGI_Metadata_20230304";
        var original = reject(model, "FileFormat", 2442, 2544, 1135, 1145);
        var schema = compileControl(model, "metadata", original);
        var type = classSymbol(schema, model + ".FileFormats.PublishedFileFormat");
        var base = find(schema, model + ".FileFormat");
        assertInstanceOf(StructureSymbol.class, schema.semanticGraph().symbol(base).orElseThrow());
        assertEquals(Optional.of(base), type.baseType());
        assertMembers(schema, model + ".FileFormats.PublishedFileFormat", List.of("name", "abbreviation", "mimeType", "extension"));
    }

    @Test void originalApplicantRejectsOuterNameAndRenamedClassKeepsMembers() throws Exception {
        String model = "SO_AWJF_Gesuchsteller_20210202";
        var original = reject(model, "Gesuchsteller", 734, 1238, 635, 648);
        var schema = compileControl(model, "applicant", original);
        String name = model + ".Gesuchsteller.Applicant";
        assertEquals(Optional.empty(), classSymbol(schema, name).baseType());
        assertMembers(schema, name, List.of("Gesuchsteller", "Forstrevier", "Forstkreis", "Abstufung_50_100", "Gesuchsnummer"));
    }

    private CompilationOutcome.Rejected reject(String model, String name,
                                                int start, int end, int relatedStart, int relatedEnd) throws Exception {
        String original = Files.readString(DATA.resolve("compiler/name-resolution/" + model + ".ili"));
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request(model, original, 1), CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
        var diagnostics = rejected.analysis().diagnostics();
        assertEquals(1, diagnostics.size());
        var diagnostic = diagnostics.getFirst();
        assertEquals("ILI-SEM-0010", diagnostic.code().value());
        assertEquals("ERROR", diagnostic.severity().name());
        assertEquals("SEMANTIC_CHECKING", diagnostic.phase().name());
        assertEquals("diagnostic.semantics.inheritance", diagnostic.messageKey());
        assertEquals(new SourceRange(SOURCE, new SourceRevision(1), start, end), diagnostic.primaryRange().orElseThrow());
        assertEquals(List.of(new DiagnosticArgument.IdentifierArgument("element", name),
                new DiagnosticArgument.TextArgument("rule",
                        "local type names must not shadow an enclosing type name/SCOPE_OUTER_TYPE_NAME")), diagnostic.arguments());
        assertEquals(1, diagnostic.relatedLocations().size());
        assertEquals(new SourceRange(SOURCE, new SourceRevision(1), relatedStart, relatedEnd),
                diagnostic.relatedLocations().getFirst().range());
        assertEquals(name, original.substring(relatedStart, relatedEnd));

        Path input = Files.writeString(folder.resolve(model + ".ili"), original);
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        var environment = new CliEnvironment(folder, InputStream.nullInputStream(),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), Map.of());
        var result = new DefaultCompilerCli().run(List.of("check", "--json", input.toString()), environment, CancellationToken.none());
        assertEquals(1, result.exitCode());
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
        var json = JsonParser.parseString(stdout.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(json.get("status").getAsString().contains("SOURCE_ERRORS"));
        var items = json.getAsJsonArray("diagnostics");
        assertEquals(1, items.size());
        var item = items.get(0).getAsJsonObject();
        assertEquals("ILI-SEM-0010", item.get("code").getAsString());
        var range = item.getAsJsonObject("range");
        assertEquals(input.toUri().toString(), range.get("source").getAsString());
        assertEquals(start, range.get("startOffset").getAsInt());
        assertEquals(end, range.get("endOffset").getAsInt());
        var related = item.getAsJsonArray("relatedLocations");
        assertEquals(1, related.size());
        assertEquals(relatedStart, related.get(0).getAsJsonObject().getAsJsonObject("range").get("startOffset").getAsInt());
        assertEquals(relatedEnd, related.get(0).getAsJsonObject().getAsJsonObject("range").get("endOffset").getAsInt());
        return rejected;
    }

    private static CompiledSchema compileControl(String model, String fixture, CompilationOutcome.Rejected original)
            throws IOException {
        String text = Files.readString(DATA.resolve("compiler/name-resolution/" + fixture + "-outer-name-control-2.3.ili"));
        var engine = CompilerEngines.create();
        var request = request(model, text, 2);
        var fresh = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request, CancellationToken.none()));
        var reuse = new AnalysisReuse(Optional.of(original.analysis()), original.analysis().syntaxSnapshots());
        var reused = assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(request, reuse, CancellationToken.none()));
        assertEquals(fresh.analysis().diagnostics(), reused.analysis().diagnostics());
        return reused.schema();
    }

    private static CompilationRequest request(String model, String text, long revision) {
        var source = new SourceText(SOURCE, new SourceRevision(revision), text);
        return new CompilationRequest(List.of(new ModelSelector.ByName(model, Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(SOURCE, source)), CompilerOptions.defaults());
    }

    private static SymbolId find(CompiledSchema schema, String name) {
        return schema.queries().findQualifiedName(new QualifiedName(List.of(name.split("\\.")))).getFirst().symbol();
    }

    private static ClassSymbol classSymbol(CompiledSchema schema, String name) {
        return assertInstanceOf(ClassSymbol.class, schema.semanticGraph().symbol(find(schema, name)).orElseThrow());
    }

    private static void assertMembers(CompiledSchema schema, String name, List<String> expected) {
        var graph = schema.semanticGraph();
        assertEquals(expected, graph.effectiveMembers(find(schema, name)).stream()
                .map(member -> graph.symbol(member.declaredSymbol()).orElseThrow().name().value()).toList());
    }
}
