package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.FormattedType;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class FormattedTypeContractTest {
    @Test
    void builtInFormatIsModeledWithBounds() {
        SourceText source = new SourceText(new SourceId("memory:/formatted.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL F AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS C =
                      value : FORMAT INTERLIS.XMLDate "2010-01-01" .. "2110-12-31";
                    END C;
                  END T;
                END F.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("F",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        FormattedType type = succeeded.analysis().semanticGraph().types().stream()
                .filter(FormattedType.class::isInstance).map(FormattedType.class::cast)
                .filter(f -> f.minimum().isPresent())
                .findFirst().orElseThrow();
        assertEquals("INTERLIS.XMLDate", type.formatName());
        assertEquals(Optional.of("2010-01-01"), type.minimum());
        assertEquals(Optional.of("2110-12-31"), type.maximum());
    }
}
