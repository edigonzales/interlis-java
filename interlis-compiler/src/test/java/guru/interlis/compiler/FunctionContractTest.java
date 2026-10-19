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
                MODEL F AT "https://example.interlis.ch" VERSION "1.0" =
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
        assertEquals("BOOLEAN", function.returnType());
    }
}
