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

class ViewFormationSemanticsContractTest {
    private static CompilationOutcome compile(String text) {
        SourceText source = new SourceText(new SourceId("memory:/view-formation.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("Test",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        return CompilerEngines.create().compile(request, CancellationToken.none());
    }

    @Test
    void abstractClassWithConcreteClassInConcreteTopicCompiles() {
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile("""
                INTERLIS 2.3;
                MODEL Test AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Base =
                    CLASS B (ABSTRACT) = a : TEXT*10; END B;
                    CLASS C EXTENDS B = END C;
                  END Base;
                END Test.
                """));
    }

    @Test
    void abstractClassWithoutAbstractTopicOrConcreteClassIsRejected() {
        assertInstanceOf(CompilationOutcome.Rejected.class, compile("""
                INTERLIS 2.3;
                MODEL Test AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Base =
                    CLASS B (ABSTRACT) = a : TEXT*10; END B;
                  END Base;
                END Test.
                """));
    }

    @Test
    void boundViewBaseExtensionProducesSchema() {
        assertInstanceOf(CompilationOutcome.Succeeded.class, compile("""
                INTERLIS 2.3;
                MODEL Test AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Base =
                    CLASS B = a : TEXT*10; END B;
                    CLASS C EXTENDS B = b : TEXT*10; END C;
                  END Base;
                  TOPIC Views =
                    DEPENDS ON Test.Base;
                    VIEW VB
                      PROJECTION OF Test.Base.B;
                      BASE B EXTENDED BY Test.Base.C
                      =
                      ATTRIBUTE
                        a := B->a;
                    END VB;
                  END Views;
                END Test.
                """));
    }
}
