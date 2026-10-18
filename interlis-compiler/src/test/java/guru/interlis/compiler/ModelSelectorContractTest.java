package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** BySource and source-hinted ByName selectors stay distinct. */
class ModelSelectorContractTest {
    private static final String TWO_MODELS = """
            INTERLIS 2.4;
            MODEL A AT "https://example.interlis.ch" VERSION "1.0" =
              TOPIC Data =
                CLASS Item =
                  value : 0..9;
                END Item;
              END Data;
            END A.
            MODEL B AT "https://example.interlis.ch" VERSION "2.0" =
              TOPIC Data =
                CLASS Item =
                  label : TEXT*20;
                END Item;
              END Data;
            END B.
            """;

    @Test
    void bySourceSelectsBothModelsInTheSource() {
        SourceText source = source();
        CompilationOutcome.Succeeded succeeded = compile(
                List.of(new ModelSelector.BySource(source.id())), source);
        assertEquals(2, succeeded.schema().models().size());
    }

    @Test
    void byNameWithVersionAndSourceHintSelectsOneModel() {
        SourceText source = source();
        CompilationOutcome.Succeeded succeeded = compile(
                List.of(new ModelSelector.ByName("B", Optional.of("2.0"),
                        Optional.of(source.id()))), source);
        assertEquals(1, succeeded.schema().models().size());
        assertEquals("B", succeeded.analysis().semanticGraph()
                .symbol(succeeded.schema().models().getFirst().symbol())
                .orElseThrow().name().value());
    }

    private static SourceText source() {
        return new SourceText(new SourceId("memory:/two.ili"), new SourceRevision(1), TWO_MODELS);
    }

    private static CompilationOutcome.Succeeded compile(List<ModelSelector> roots,
                                                         SourceText source) {
        CompilationRequest request = new CompilationRequest(roots,
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
