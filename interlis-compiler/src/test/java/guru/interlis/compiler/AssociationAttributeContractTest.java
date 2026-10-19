package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AssociationAttributeContractTest {
    @Test
    void attributeStyleAssociationPropertyIsIndexed() {
        SourceText source = new SourceText(new SourceId("memory:/association-attribute.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL M AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = END A;
                    ASSOCIATION AA =
                      a1 -- A;
                      a2 -- A;
                      note : TEXT*20;
                    END AA;
                  END T;
                END M.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("M",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        AssociationSymbol association = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(AssociationSymbol.class::isInstance).map(AssociationSymbol.class::cast)
                .findFirst().orElseThrow();
        assertEquals(1, association.attributes().size());
        AttributeSymbol attribute = assertInstanceOf(AttributeSymbol.class,
                succeeded.analysis().semanticGraph().symbol(
                        association.attributes().getFirst()).orElseThrow());
        assertEquals("note", attribute.name().value());
    }
}
