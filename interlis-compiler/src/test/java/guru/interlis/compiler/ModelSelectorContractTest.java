package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void validHeaderWithoutModelsParsesButDoesNotSelectARoot() {
        for (String text : List.of("INTERLIS 2.4;", "/* valid */ INTERLIS 2.4; !! after the header")) {
            SourceText empty = new SourceText(new SourceId("memory:/empty.ili"), new SourceRevision(2), text);
            var syntax = SyntaxServices.create().parse(empty, ParseOptions.defaults(ParseMode.STRICT),
                    CancellationToken.none());
            assertTrue(syntax.consumedEntireInput());
            assertEquals(List.of(), syntax.diagnostics());
            assertEquals(List.of(), syntax.root().models());

            SourceText valid = source();
            var emptyRoot = new ModelSelector.BySource(empty.id());
            for (var roots : List.<List<ModelSelector>>of(List.of(emptyRoot),
                    List.of(new ModelSelector.BySource(valid.id()), emptyRoot))) {
                var request = new CompilationRequest(roots, SourceSet.of(Map.of(empty.id(), empty, valid.id(), valid)),
                        CompilerOptions.defaults());
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                        CompilerEngines.create().compile(request, CancellationToken.none()));
                assertEquals(Set.of(RejectionReason.MISSING_INPUT), rejected.rejection().reasons());
                var diagnostic = rejected.analysis().requestDiagnostics().getFirst();
                assertEquals("ILI-RES-0001", diagnostic.code().value());
                assertTrue(diagnostic.arguments().stream().anyMatch(argument ->
                        argument instanceof guru.interlis.diagnostics.DiagnosticArgument.IdentifierArgument identifier
                                && identifier.value().equals(empty.id().value())));
            }
        }
    }

    @Test
    void damagedHeaderWithoutModelsReportsSourceErrorsEvenBesideValidRoot() {
        SourceText damaged = new SourceText(new SourceId("memory:/damaged.ili"), new SourceRevision(3), "INTERLIS 2.4");
        SourceText valid = source();
        var request = new CompilationRequest(
                List.of(new ModelSelector.BySource(valid.id()), new ModelSelector.BySource(damaged.id())),
                SourceSet.of(Map.of(damaged.id(), damaged, valid.id(), valid)), CompilerOptions.defaults());
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
        assertTrue(rejected.analysis().requestDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().value().equals("ILI-SYN-0005")
                        && diagnostic.primaryRange().orElseThrow().source().equals(damaged.id())));
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
