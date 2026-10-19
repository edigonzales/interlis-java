package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidRuleContractTest {
    @Test
    void basketOidRuleRemainsVisibleOnTopic() {
        SourceText source = new SourceText(new SourceId("memory:/basket-oid.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL O AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    BASKET OID AS INTERLIS.UUIDOID;
                    CLASS C = END C;
                  END T;
                END O.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("O",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        guru.interlis.model.TopicSymbol topic = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(guru.interlis.model.TopicSymbol.class::isInstance)
                .map(guru.interlis.model.TopicSymbol.class::cast).findFirst().orElseThrow();
        assertTrue(topic.header().oidRule().orElseThrow().contains("BASKET"));
    }

    @Test
    void classOidRuleRemainsVisible() {
        SourceText source = new SourceText(new SourceId("memory:/oid.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL O AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS C =
                      OID AS UUIDOID;
                    END C;
                  END T;
                END O.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("O",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ClassSymbol clazz = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .findFirst().orElseThrow();
        assertTrue(clazz.header().oidRule().isPresent());
        assertTrue(clazz.header().oidRule().orElseThrow().contains("OID"));
    }
}
