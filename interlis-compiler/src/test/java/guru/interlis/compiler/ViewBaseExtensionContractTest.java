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

class ViewBaseExtensionContractTest {
    @Test
    void unrelatedBaseExtensionIsRejected() {
        SourceText source = new SourceText(new SourceId("memory:/view-base.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL V AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS B = a : TEXT*10; END B;
                    CLASS C = b : TEXT*10; END C;
                    VIEW VB
                      PROJECTION OF B;
                      BASE B EXTENDED BY C
                      =
                      ATTRIBUTE
                        ALL OF B;
                    END VB;
                  END T;
                END V.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("V",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
