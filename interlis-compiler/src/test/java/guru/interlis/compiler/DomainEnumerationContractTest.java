package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DomainEnumerationContractTest {
    @Test
    void domainEnumerationIsAccepted() {
        SourceText source = new SourceText(new SourceId("memory:/domain-enum.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL D AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN Colour = (red, blue);
                  END T;
                END D.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("D",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        DomainSymbol domain = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast)
                .findFirst().orElseThrow();
        EnumerationType type = assertInstanceOf(EnumerationType.class,
                succeeded.analysis().semanticGraph().type(domain.typeId()).orElseThrow());
        assertEquals(2, type.values().size());
    }
}
