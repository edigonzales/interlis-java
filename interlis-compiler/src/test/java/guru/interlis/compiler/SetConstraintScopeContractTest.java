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

class SetConstraintScopeContractTest {
    @Test
    void setConstraintInStructureIsRejected() {
        SourceText source = new SourceText(new SourceId("memory:/set-structure.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL S AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    STRUCTURE X =
                      value : TEXT*10;
                      SET CONSTRAINT #true;
                    END X;
                  END T;
                END S.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("S",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
