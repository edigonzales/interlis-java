package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ExpressionConstraintContractTest {
    private static CompilationOutcome compile(String text) {
        SourceText source = new SourceText(new SourceId("memory:/expression-constraint.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("M",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    @Test
    void builtinFunctionAndImplicationCompile() {
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A =
                      attr : TEXT*10;
                      MANDATORY CONSTRAINT INTERLIS.len(attr) >= 0;
                      MANDATORY CONSTRAINT #true => #true;
                    END A;
                  END T;
                END M.
                """));
    }

    @Test
    void existenceTypeMismatchIsRejected() {
        assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = attr : TEXT*10; END A;
                    CLASS B =
                      number : 1 .. 10;
                      EXISTENCE CONSTRAINT number REQUIRED IN A:attr;
                    END B;
                  END T;
                END M.
                """));
    }
}
