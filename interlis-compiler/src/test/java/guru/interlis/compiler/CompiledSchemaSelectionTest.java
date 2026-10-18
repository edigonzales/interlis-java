package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

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
}
