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

class ViewDependencyContractTest {
    @Test
    void qualifiedViewFormationRequiresDependsOn() {
        SourceText source = new SourceText(new SourceId("memory:/view-dependency.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL V AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Base =
                    CLASS B = value : TEXT*10; END B;
                  END Base;
                  TOPIC Views =
                    VIEW VB
                      PROJECTION OF V.Base.B;
                      =
                      ATTRIBUTE ALL OF B;
                    END VB;
                  END Views;
                END V.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("V",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
