package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ScopeId;
import guru.interlis.model.SymbolRef;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only the requested closure may appear in a successful CompiledSchema. */
class CompiledSchemaSelectionTest {
    private static final String BASE = """
            INTERLIS 2.4;
            MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
              DOMAIN Count = 0..999;
              TOPIC Common =
                CLASS BaseBuilding =
                  height : Count;
                END BaseBuilding;
              END Common;
            END Base.
            """;
    private static final String SURVEY = """
            INTERLIS 2.4;
            MODEL Survey AT "https://example.interlis.ch" VERSION "1.0" =
              IMPORTS Base;
              TOPIC Data =
                DEPENDS ON Base.Common;
                CLASS Building EXTENDS Base.Common.BaseBuilding =
                  height (EXTENDED) : Base.Count;
                END Building;
              END Data;
            END Survey.
            """;
    private static final String UNRELATED_BROKEN = """
            INTERLIS 2.4;
            MODEL Unrelated AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Broken =
                  value : ;
                END Broken;
              END Data;
            END Unrelated.
            GARBAGE
            """;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void referenceOccurrencesBelongToTheSelectedModels(boolean sharedSource) {
        String header = "INTERLIS 2.4;\n";
        String rootModel = """
                MODEL Root AT "x" VERSION "1" =
                  IMPORTS Base;
                  STRUCTURE Holder = label : INTERLIS.NAME; value : Base.D; END Holder;
                END Root.
                """;
        String otherModel = """
                MODEL Other AT "x" VERSION "1" =
                  STRUCTURE Holder = label : INTERLIS.NAME; END Holder;
                END Other.
                """;
        var root = source("root.ili", header + rootModel + (sharedSource ? otherModel : ""));
        var other = sharedSource ? root : source("other.ili", header + otherModel);
        var base = source("base.ili", header + """
                MODEL Base AT "x" VERSION "1" =
                  DOMAIN D = TEXT;
                  STRUCTURE Holder = label : INTERLIS.NAME; END Holder;
                END Base.
                """);
        var sources = new LinkedHashMap<SourceId, SourceText>();
        for (var source : List.of(root, other, base)) sources.put(source.id(), source);
        var binding = CompilerTestSupport.binding(CompilerTestSupport.parse(root), "Base", base,
                CompilerTestSupport.parse(base));
        var options = CompilerOptions.defaults();
        var request = CompilerTestSupport.request(List.of("Root"), sources, List.of(binding), options);
        var engine = CompilerEngines.create();
        var token = CancellationToken.none();
        var outcome = engine.compile(request, token);
        var success = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, () -> outcome instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().diagnostics().toString() : outcome.toString());
        var schema = success.schema();
        var graph = schema.semanticGraph();
        var fullGraph = success.analysis().semanticGraph();
        var nameRef = graph.references().stream().filter(ref -> sources.get(ref.range().source()).slice(ref.range()).equals("INTERLIS.NAME"))
                .findFirst().orElseThrow();
        var name = nameRef.segments().getLast().target().orElseThrow();
        var handle = new SymbolRef(schema.snapshotId(), name);
        var fullRefs = fullGraph.references().stream().filter(ref -> sources.get(ref.range().source()).slice(ref.range()).equals("INTERLIS.NAME")).toList();
        assertEquals(3, fullRefs.size());
        var expected = fullRefs.stream().filter(ref -> ref.range().source().equals(base.id())
                || ref.range().source().equals(root.id()) && ref.range().startOffset() < header.length() + rootModel.length())
                .toList();
        assertEquals(2, expected.size());
        assertEquals(expected, graph.references().stream().filter(ref -> sources.get(ref.range().source()).slice(ref.range()).equals("INTERLIS.NAME")).toList());
        assertEquals(expected, schema.queries().referencesTo(handle));
        for (var ref : expected) {
            assertTrue(graph.symbols().stream().anyMatch(s -> ScopeId.forOwner(s.id()).equals(ref.originalScope())));
            assertEquals("INTERLIS.NAME", sources.get(ref.range().source()).text()
                    .substring(ref.range().startOffset(), ref.range().endOffset()));
        }
        var imported = graph.references().stream().filter(ref -> sources.get(ref.range().source()).slice(ref.range()).equals("Base.D")).findFirst().orElseThrow();
        assertTrue(fullGraph.references().contains(imported));
        assertEquals(List.of("Base", "D"), imported.segments().stream().map(segment -> segment.text()).toList());
        assertTrue(imported.segments().stream().allMatch(segment ->
                segment.target().flatMap(graph::symbol).isPresent()));

        var otherRequest = CompilerTestSupport.request(List.of("Other"), sources, List.of(binding), options);
        var reuse = new AnalysisReuse(Optional.of(success.analysis()), success.analysis().syntaxSnapshots());
        assertInstanceOf(CompilationOutcome.Succeeded.class, engine.compile(otherRequest, reuse, token));
        assertEquals(expected, schema.queries().referencesTo(handle));
    }

    @Test
    void unrelatedBrokenModelStaysOutsideTheCompiledClosure() {
        SourceText base = source("base.ili", BASE);
        SourceText survey = source("survey.ili", SURVEY);
        SourceText broken = source("unrelated.ili", UNRELATED_BROKEN);
        SyntaxSnapshot baseSyntax = CompilerTestSupport.parse(base);
        SyntaxSnapshot surveySyntax = CompilerTestSupport.parse(survey);
        ImportBinding binding = CompilerTestSupport.binding(surveySyntax, "Base", base, baseSyntax);
        Map<SourceId, SourceText> sources = new LinkedHashMap<>();
        sources.put(base.id(), base);
        sources.put(survey.id(), survey);
        sources.put(broken.id(), broken);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Survey", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(sources, List.of(binding), List.of(),
                        new ResolutionFingerprint("test-resolver/selection")),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        List<String> models = succeeded.schema().models().stream()
                .map(ref -> succeeded.analysis().semanticGraph().symbol(ref.symbol()))
                .flatMap(Optional::stream)
                .map(symbol -> symbol.name().value())
                .toList();
        assertEquals(2, models.size(), models.toString());
        assertTrue(models.stream().anyMatch(name -> name.endsWith("Base")), models.toString());
        assertTrue(models.stream().anyMatch(name -> name.endsWith("Survey")), models.toString());
        assertTrue(models.stream().noneMatch(name -> name.contains("Unrelated")), models.toString());
    }

    private static SourceText source(String name, String text) {
        return new SourceText(new SourceId("memory:/" + name), new SourceRevision(1), text);
    }

    @Test
    void unrelatedSemanticErrorInSameFileDoesNotBlockSelectedModel() {
        SourceText source = source("shared.ili", """
                INTERLIS 2.4;
                MODEL Good AT "x" VERSION "1" =
                  TOPIC T = CLASS C = value: TEXT; END C; END T;
                END Good.
                MODEL Bad AT "x" VERSION "1" =
                  TOPIC T = CLASS C = value: Missing; END C; END T;
                END Bad.
                """);
        var request = CompilerTestSupport.request(List.of("Good"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
        var succeeded = assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        assertEquals(1, succeeded.schema().models().size());
        assertTrue(succeeded.analysis().diagnostics().stream()
                .anyMatch(d -> d.severity() == guru.interlis.diagnostics.Severity.ERROR));
        var invalid = CompilerTestSupport.request(List.of("Bad"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(invalid, CancellationToken.none()));
    }
}
