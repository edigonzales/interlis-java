package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.Modifier;
import guru.interlis.model.TopicSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierContractTest {
    @Test
    void abstractAndExtendedModifiersRemainVisibleOnSymbols() {
        SourceText source = new SourceText(new SourceId("memory:/modifiers.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL Modifiers AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Data (ABSTRACT) =
                    CLASS Item (EXTENDED) =
                      value : 0..9;
                    END Item;
                  END Data;
                END Modifiers.
                """);
        CompilationRequest request = new CompilationRequest(
                List.of(new ModelSelector.ByName("Modifiers", Optional.empty(),
                        Optional.empty())),
                SourceSet.of(Map.of(source.id(), source)), CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        TopicSymbol topic = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(TopicSymbol.class::isInstance).map(TopicSymbol.class::cast)
                .findFirst().orElseThrow();
        ClassSymbol item = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .findFirst().orElseThrow();
        assertTrue(topic.header().modifiers().contains(Modifier.ABSTRACT));
        assertTrue(item.header().modifiers().contains(Modifier.EXTENDED));
    }
}
