package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ViewSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewContractTest {
    @Test
    void duplicateAllOfAttributeIsRejected() {
        SourceText source = new SourceText(new SourceId("memory:/view-duplicate.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL V AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Base =
                    CLASS B = a : TEXT*10; END B;
                    CLASS C = a : TEXT*10; END C;
                  END Base;
                  TOPIC Views =
                    DEPENDS ON V.Base;
                    VIEW BC
                      JOIN OF B ~ V.Base.B, C ~ V.Base.C;
                      =
                      ATTRIBUTE
                        ALL OF B;
                        ALL OF C;
                    END BC;
                  END Views;
                END V.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("V",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }

    @Test
    void projectionPublishesBoundFormationAndMembers() {
        SourceText source = new SourceText(new SourceId("memory:/view.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL V AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS B = value : TEXT*10; END B;
                    VIEW VB
                      PROJECTION OF B;
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
        var succeeded = assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ViewSymbol view = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ViewSymbol.class::isInstance).map(ViewSymbol.class::cast).findFirst().orElseThrow();
        assertInstanceOf(ViewSymbol.Projection.class, view.formation().orElseThrow());
        assertTrue(view.members().size() == 1);
        var graph = succeeded.analysis().semanticGraph();
        var attribute = (guru.interlis.model.AttributeSymbol) graph.symbol(view.members().getFirst()).orElseThrow();
        assertTrue(attribute.derivations().size() == 1);
        assertTrue(graph.expression(attribute.derivations().getFirst()).isPresent());
    }
}
