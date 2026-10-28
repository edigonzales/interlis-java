package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** K1: a value's whole range must fit its destination (2.3 §2.6 / 2.4 §3.6). */
class NormativeViewAssignmentTest {
    private CompilationOutcome compile(String version, String body) {
        var source = new SourceText(new SourceId("memory:/assignment.ili"), new SourceRevision(1),
                "INTERLIS " + version + "; CONTRACTED MODEL M AT \"x\" VERSION \"1\" = " + body + " END M.");
        return CompilerEngines.create().compile(new CompilationRequest(List.of(new ModelSelector.BySource(source.id())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults()), CancellationToken.none());
    }

    private void assertBounds(CompilationOutcome outcome, String actual, String expected) {
        var rejected = assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
        var diagnostics = rejected.analysis().diagnostics();
        assertEquals(1, diagnostics.size(), diagnostics::toString);
        var diagnostic = diagnostics.getFirst();
        assertEquals("ILI-SEM-0005", diagnostic.code().value());
        assertEquals(List.of(actual, expected), diagnostic.arguments().stream().map(a -> ((guru.interlis.diagnostics.DiagnosticArgument.TypeNameArgument) a).value()).toList());
    }

    private void assertDerivedText(CompilationOutcome outcome, int length) {
        var succeeded = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
        var graph = succeeded.schema().semanticGraph();
        var view = graph.symbols().stream().filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
        var attribute = (AttributeSymbol) graph.symbol(view.members().getFirst()).orElseThrow();
        assertEquals(length, ((TextType) graph.type(attribute.declaredType()).orElseThrow()).maxLength().orElseThrow());
        var expression = graph.expression(attribute.derivations().getFirst()).orElseThrow();
        assertEquals(length, ((TextType) graph.type(expression.typeId()).orElseThrow()).maxLength().orElseThrow());
    }

    @Test void parentTextRangeIsNotSilentlyNarrowedAndDiagnosticsShowBothBounds() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "TOPIC T = STRUCTURE S = END S; CLASS A = items: BAG OF S; label: TEXT*20; END A; "
                    + "VIEW V INSPECTION OF a~A->items; = name: TEXT*10 := PARENT->label; END V; END T;";
            assertBounds(compile(version, body), "TEXT*20", "TEXT*10");
            assertDerivedText(compile(version, body.replace("name: TEXT*10", "name: TEXT*20")), 20);
        }
    }

    @Test void functionTextResultUsesItsSignatureRatherThanItsLiteralArgument() {
        for (var version : List.of("2.3", "2.4")) {
            var body = "FUNCTION f(text: TEXT): TEXT; TOPIC T = CLASS A = END A; "
                    + "VIEW V PROJECTION OF A; = name: TEXT*80 := f(\"short\"); END V; END T;";
            assertBounds(compile(version, body), "TEXT", "TEXT*80");
            assertDerivedText(compile(version, body.replace("): TEXT;", "): TEXT*80;")), 80);
        }
    }
}
