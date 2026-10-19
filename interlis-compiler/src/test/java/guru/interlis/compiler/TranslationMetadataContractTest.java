package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.ModelSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TranslationMetadataContractTest {
    @Test
    void translationHeaderIsPreserved() {
        SourceText source = new SourceText(new SourceId("memory:/translation.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL Base AT "https://example.interlis.ch" VERSION "1.0" =
                END Base.
                MODEL BaseDe (de) AT "https://example.interlis.ch" VERSION "1.0"
                TRANSLATION OF Base [ "1.0" ] =
                END BaseDe.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("BaseDe",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        ModelSymbol model = succeeded.analysis().semanticGraph().symbols().stream()
                .filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast)
                .filter(symbol -> symbol.name().value().equals("BaseDe"))
                .findFirst().orElseThrow();
        assertEquals("Base", model.translation().orElseThrow().sourceModel());
        assertEquals(Optional.of("1.0"), model.translation().orElseThrow().sourceVersion());
        assertEquals(Optional.of("de"), model.translation().orElseThrow().language());
    }
}
