package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CoordinateType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CoordinateReferenceSystemContractTest {
    @Test
    void referenceSystemIsPreserved() {
        SourceText source = new SourceText(new SourceId("memory:/coord.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL C AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS P =
                      position : COORD 0.000 .. 100.000, 0.000 .. 100.000
                        REFSYS "EPSG:2056";
                    END P;
                  END T;
                END C.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("C",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        CoordinateType coordinate = succeeded.analysis().semanticGraph().types().stream()
                .filter(CoordinateType.class::isInstance).map(CoordinateType.class::cast)
                .findFirst().orElseThrow();
        assertEquals("EPSG:2056", coordinate.referenceSystem().orElseThrow());
    }
}
