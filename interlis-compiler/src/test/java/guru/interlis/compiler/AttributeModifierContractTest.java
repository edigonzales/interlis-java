package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeModifierContractTest {
    @Test
    void abstractAttributeModifierRemainsVisible() {
        SourceText source = new SourceText(new SourceId("memory:/abstract-attribute.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL A AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T (ABSTRACT) =
                    CLASS C (ABSTRACT) =
                      value (ABSTRACT) : TEXT*10;
                    END C;
                  END T;
                END A.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("A",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ClassSymbol clazz = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .findFirst().orElseThrow();
        AttributeSymbol attribute = assertInstanceOf(AttributeSymbol.class,
                succeeded.analysis().semanticGraph().symbol(clazz.members().getFirst()).orElseThrow());
        assertTrue(attribute.header().modifiers().contains(Modifier.ABSTRACT));
    }
}
