package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalConstraintContractTest {
    @Test
    void externalConstraintKeepsTarget() {
        SourceText source = new SourceText(new SourceId("memory:/external-constraint.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL C AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A =
                      value : TEXT*10;
                    END A;
                    CONSTRAINTS OF A =
                      UNIQUE value;
                    END;
                  END T;
                END C.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("C",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ConstraintSymbol constraint = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ConstraintSymbol.class::isInstance).map(ConstraintSymbol.class::cast)
                .findFirst().orElseThrow();
        assertTrue(constraint.target().isPresent());
    }
}
