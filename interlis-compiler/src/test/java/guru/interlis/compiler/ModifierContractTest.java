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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

class ModifierContractTest {
    @Test
    void abstractAndExtendedModifiersRemainVisibleOnSymbols() {
        SourceText source = new SourceText(new SourceId("memory:/modifiers.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL Modifiers AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC Base (ABSTRACT) = CLASS Item = END Item; END Base;
                  TOPIC Data (ABSTRACT) EXTENDS Base =
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
                .filter(c -> c.header().modifiers().contains(Modifier.EXTENDED)).findFirst().orElseThrow();
        assertTrue(topic.header().modifiers().contains(Modifier.ABSTRACT));
        assertTrue(item.header().modifiers().contains(Modifier.EXTENDED));
    }

    @Test
    void propertyListsOnlyAcceptModifiersPermittedForTheDeclarationContext() {
        for (String version : List.of("2.3", "2.4")) {
            var source = new SourceText(new SourceId("memory:/properties.ili"), new SourceRevision(1),
                    "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "
                            + "TOPIC T (ABSTRACT) = CLASS C (ABSTRACT) = END C; END T; END F.");
            var request = CompilerTestSupport.request(List.of("F"), Map.of(source.id(), source),
                    List.of(), CompilerOptions.defaults());
            assertInstanceOf(CompilationOutcome.Succeeded.class,
                    CompilerEngines.create().compile(request, CancellationToken.none()));

            var invalidSource = new SourceText(new SourceId("memory:/invalid-properties.ili"), new SourceRevision(1),
                    "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = "
                            + "TOPIC T = CLASS C (TRANSIENT) = END C; END T; END F.");
            var invalidRequest = CompilerTestSupport.request(List.of("F"), Map.of(invalidSource.id(), invalidSource),
                    List.of(), CompilerOptions.defaults());
            var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                    CompilerEngines.create().compile(invalidRequest, CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejected.rejection().reasons());
        }
    }
}
