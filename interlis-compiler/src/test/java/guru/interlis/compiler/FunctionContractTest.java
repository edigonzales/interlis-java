package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FunctionContractTest {
    @Test
    void functionSignatureIsModeled() {
        SourceText source = new SourceText(new SourceId("memory:/function.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                CONTRACTED MODEL F AT "https://example.interlis.ch" VERSION "1.0" =
                  FUNCTION isTrue (value: BOOLEAN) : BOOLEAN;
                END F.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("F",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        FunctionSymbol function = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(FunctionSymbol.class::isInstance).map(FunctionSymbol.class::cast)
                .findFirst().orElseThrow();
        assertEquals("isTrue", function.name().value());
        assertEquals(1, function.parameters().size());
        assertEquals("value", function.parameters().getFirst().name());
        assertInstanceOf(guru.interlis.model.BooleanType.class,
                succeeded.analysis().semanticGraph().type(function.returnType()).orElseThrow());
    }
    private static CompilationOutcome compile(String version, boolean contracted,
                                               String signature, String expression) {
        SourceText source = new SourceText(new SourceId("memory:/call.ili"),
                new SourceRevision(1), "INTERLIS " + version + "; "
                + (contracted ? "CONTRACTED " : "")
                + "MODEL F AT \"x\" VERSION \"1\" = "
                + "FUNCTION f " + signature + "; "
                + "TOPIC T = CLASS C = MANDATORY CONSTRAINT " + expression
                + "; END C; END T; END F.");
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("F",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    @Test
    void checksArgumentCountAndTypeAndKeepsNumericReturnType() {
        assertInstanceOf(CompilationOutcome.Rejected.class,
                compile("2.3", true, "(v: BOOLEAN): BOOLEAN", "f(#true, #false)"));
        assertInstanceOf(CompilationOutcome.Rejected.class,
                compile("2.3", true, "(v: BOOLEAN): BOOLEAN", "f(123)"));
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                compile("2.3", true, "(v: NUMERIC): NUMERIC", "f(1) > 0"));
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                compile("2.3", true, "(v: BOOLEAN): BOOLEAN", "F.f(#true)"));
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                compile("2.3", true, "(v: TEXT*3): BOOLEAN", "f(\"abc\")"));
        assertInstanceOf(CompilationOutcome.Rejected.class,
                compile("2.3", true, "(v: TEXT*3): BOOLEAN", "f(\"abcd\")"));
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                compile("2.4", false, "(v: TEXT*1): BOOLEAN", "f(\"\\u0041\")"));
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                compile("2.4", false, "(v: MTEXT): BOOLEAN", "f(\"abc\")"));
    }

    @Test
    void contractedIsRequiredOnlyIn23() {
        assertInstanceOf(CompilationOutcome.Rejected.class,
                compile("2.3", false, "(v: BOOLEAN): BOOLEAN", "f(#true)"));
        for (boolean contracted : List.of(false, true)) {
            assertInstanceOf(CompilationOutcome.Succeeded.class,
                    compile("2.4", contracted, "(v: BOOLEAN): BOOLEAN", "f(#true)"));
        }
    }

    private static CompilationOutcome builtin(String version, String expression) {
        var source = CompilerTestSupport.source("memory:/builtin.ili",
                "INTERLIS " + version + "; MODEL F AT \"x\" VERSION \"1\" ="
                        + " TOPIC T = CLASS C = label : TEXT*20; multiline : MTEXT*80;"
                        + " MANDATORY CONSTRAINT " + expression + "; END C; END T; END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                CancellationToken.none());
    }

    @Test
    void standardFunctionsHaveExplicitCaseSensitiveSignatures() {
        for (String version : List.of("2.3", "2.4")) {
            for (String expression : List.of("INTERLIS.len(label) >= 0",
                    "INTERLIS.lenM(multiline) >= 0",
                    "INTERLIS.trim(label) == label",
                    "INTERLIS.trimM(multiline) == multiline")) {
                assertInstanceOf(CompilationOutcome.Succeeded.class, builtin(version, expression), expression);
            }
            for (String expression : List.of("INTERLIS.len(123) >= 0",
                    "INTERLIS.len(label, label) >= 0", "INTERLIS.len() >= 0",
                    "INTERLIS.LEN(label) >= 0", "INTERLIS.unknownBoolean()",
                    "INTERLIS.objectCount(#true) >= 0", "INTERLIS.elementCount(label) >= 0")) {
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                        builtin(version, expression), expression);
                org.junit.jupiter.api.Assertions.assertFalse(
                        rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT),
                        rejected.rejection().toString());
            }
        }
        var success = assertInstanceOf(CompilationOutcome.Succeeded.class,
                builtin("2.4", "INTERLIS.len(label) >= 0"));
        var call = success.analysis().semanticGraph().expressions().stream()
                .filter(guru.interlis.model.FunctionCallExpression.class::isInstance)
                .findFirst().orElseThrow();
        var type = assertInstanceOf(guru.interlis.model.NumericType.class,
                success.analysis().semanticGraph().type(call.typeId()).orElseThrow());
        assertEquals(guru.interlis.model.NumericRange.unbounded(), type.range());
    }

}
