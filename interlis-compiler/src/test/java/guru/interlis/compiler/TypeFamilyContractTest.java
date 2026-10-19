package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AlignmentType;
import guru.interlis.model.BlackboxType;
import guru.interlis.model.OidType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TypeFamilyContractTest {
    @Test
    void oidBlackboxAndAlignmentTypesAreModeled() {
        SourceText source = new SourceText(new SourceId("memory:/types.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL Types AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS C =
                      oid : OID ANY;
                      uuid : UUIDOID;
                      box : BLACKBOX XML;
                      align : HALIGNMENT;
                    END C;
                  END T;
                END Types.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("Types",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        var types = succeeded.analysis().semanticGraph().types();
        assertEquals(2, types.stream().filter(OidType.class::isInstance).count());
        assertEquals(1, types.stream().filter(BlackboxType.class::isInstance).count());
        assertEquals(1, types.stream().filter(AlignmentType.class::isInstance).count());
    }
}
