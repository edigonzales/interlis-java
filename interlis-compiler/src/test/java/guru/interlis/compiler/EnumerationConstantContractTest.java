package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnumerationConstantContractTest {
    private static final CancellationToken TOKEN = CancellationToken.none();

    @Test
    void logicalEnumerationConstantIsBoolean() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC T = CLASS A = value : BOOLEAN; "
                    + "MANDATORY CONSTRAINT #true; MANDATORY CONSTRAINT NOT(#false); "
                    + "MANDATORY CONSTRAINT #true AND #false OR #true; "
                    + "MANDATORY CONSTRAINT #true == #false; "
                    + "UNIQUE WHERE #true: value; "
                    + "SET CONSTRAINT WHERE #true: INTERLIS.objectCount(ALL) >= 0; "
                    + (version.equals("2.4") ? "MANDATORY CONSTRAINT #true => #false; " : "")
                    + "END A; VIEW V PROJECTION OF A; WHERE #true; = flag := #true; END V; END T;";
            var graph = ok(version, body).schema().semanticGraph();
            var constants = constants(graph);
            assertFalse(constants.isEmpty());
            assertTrue(constants.stream().allMatch(c -> graph.type(c.typeId()).orElseThrow() instanceof BooleanType));
            assertTrue(graph.references().stream().noneMatch(ref -> ref.segments().stream()
                    .anyMatch(segment -> segment.text().equals("true") || segment.text().equals("false"))));
        }
    }

    @Test
    void concreteEnumContextWinsOverBooleanSpellingAndRetainsNavigation() {
        for (String version : List.of("2.3", "2.4")) {
            String body = """
                    DOMAIN Choice = (true,false,unknown); Caps = (True,False);
                    FUNCTION check(choice: Choice; flag: BOOLEAN): BOOLEAN;
                    TOPIC T = CLASS A =
                      choice (TRANSIENT) : Choice := #true;
                      caps : Caps;
                      flag : BOOLEAN;
                      MANDATORY CONSTRAINT choice == #true;
                      MANDATORY CONSTRAINT #True == caps;
                      MANDATORY CONSTRAINT flag == #true;
                      MANDATORY CONSTRAINT check(#true,#false);
                    END A; END T;
                    """;
            var result = ok(version, body);
            var graph = result.schema().semanticGraph();
            assertEquals(4, constants(graph).stream()
                    .filter(c -> effectiveType(graph, c.typeId()) instanceof EnumerationType).count());
            assertEquals(2, constants(graph).stream()
                    .filter(c -> graph.type(c.typeId()).orElseThrow() instanceof BooleanType).count());
            var refs = graph.references().stream().filter(ref -> ref.segments().getLast().target()
                    .flatMap(graph::symbol).filter(EnumElementSymbol.class::isInstance).isPresent()).toList();
            assertEquals(4, refs.size());
            for (var ref : refs) {
                var source = result.analysis().syntaxSnapshots().get(ref.range().source()).source();
                assertEquals(ref.segments().getLast().text(), source.slice(ref.range()));
            }
            assertEquals(2, refs.stream().map(ref -> ref.segments().getLast().target()).distinct().count());
        }
    }

    @Test
    void invalidBooleanSpellingsKeepTheOriginalConstantRange() {
        for (String version : List.of("2.3", "2.4")) {
            for (String spelling : List.of("#TRUE", "#True", "#FALSE", "#False")) {
                var source = source(version, "CLASS A = flag : BOOLEAN; MANDATORY CONSTRAINT flag == " + spelling + "; END A;");
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, compile(source));
                assertTrue(rejected.rejection().reasons().contains(RejectionReason.SOURCE_ERRORS));
                assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SEM-0005")
                        && source.slice(d.primaryRange().orElseThrow()).equals(spelling)));
                var graph = rejected.analysis().semanticGraph();
                var constant = constants(graph).getFirst();
                assertEquals(spelling, constant.constantName());
                assertEquals(ExpressionTypeState.ERROR, constant.typeState());
                assertInstanceOf(ErrorType.class, graph.type(constant.typeId()).orElseThrow());
            }
            assertInstanceOf(CompilationOutcome.Rejected.class, compile(source(version,
                    "TOPIC T = CLASS A = END A; VIEW V PROJECTION OF A; = flag := #TRUE; END V; END T;")));
            assertInstanceOf(CompilationOutcome.Rejected.class, compile(source(version,
                    "CLASS A = MANDATORY CONSTRAINT #TRUE == #TRUE; END A;")));
        }
    }

    @Test
    void literalOnlyPolymorphicCallsDoNotInventABooleanAnchor() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = ok(version, "FUNCTION f(value: ENUMVAL): BOOLEAN; "
                    + "CLASS C = MANDATORY CONSTRAINT f(#true); END C;").schema().semanticGraph();
            var value = constants(graph).getFirst();
            assertEquals("#true", value.constantName());
            assertInstanceOf(EnumArgumentType.class, effectiveType(graph, value.typeId()));
        }
    }

    private static TypeDescriptor effectiveType(SemanticGraph graph, TypeId id) {
        var relations = new guru.interlis.model.query.TypeRelations(graph);
        var resolved = relations.resolveAliases(new TypeRef(graph.snapshotId(), id)).known().orElseThrow();
        return graph.type(resolved.effectiveType()).orElseThrow();
    }

    private static List<ConstantExpression> constants(SemanticGraph graph) {
        return graph.expressions().stream().filter(ConstantExpression.class::isInstance)
                .map(ConstantExpression.class::cast).toList();
    }

    private static SourceText source(String version, String body) {
        return CompilerTestSupport.source("memory:/enum-constant.ili",
                "INTERLIS " + version + "; CONTRACTED MODEL C AT \"https://example.org\" VERSION \"1\" = " + body + " END C.");
    }

    private static CompilationOutcome compile(SourceText source) {
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("C"), Map.of(source.id(), source),
                List.of(), CompilerOptions.defaults()), TOKEN);
    }

    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var outcome = compile(source(version, body));
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> outcome instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : outcome.toString());
    }
}
