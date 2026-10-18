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

/** SDK-001: the minimal string-based compiler case uses only public API. */
class PublicSdkConstructionTest {
    @Test
    void minimalPublicApiCompile() {
        SourceText source = new SourceText(new SourceId("memory:/sdk.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL Sdk AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data =
                    CLASS Item =
                      label : TEXT*20;
                    END Item;
                  END Data;
                END Sdk.
                """);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Sdk", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
