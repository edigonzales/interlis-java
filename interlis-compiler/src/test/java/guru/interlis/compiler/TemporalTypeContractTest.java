package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.TemporalKind;
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

class TemporalTypeContractTest {
    @Test
    void dateTimeKeywordsUseTheBoundPredefinedFormats() {
        SourceText source = new SourceText(new SourceId("memory:/temporal.ili"),
                new SourceRevision(1), """
                INTERLIS 2.4;
                MODEL Times AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS Event =
                      day : DATE;
                      timeOfDay : TIMEOFDAY;
                      stamp : DATETIME;
                    END Event;
                  END T;
                END Times.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("Times",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        CompilationOutcome.Succeeded succeeded = assertInstanceOf(
                CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
        var kinds = succeeded.analysis().semanticGraph().types().stream()
                .filter(FormattedType.class::isInstance).map(FormattedType.class::cast)
                .map(t -> t.predefined().orElseThrow()).toList();
        assertEquals(java.util.Set.of(TemporalKind.DATE, TemporalKind.TIMEOFDAY, TemporalKind.DATETIME),
                java.util.Set.copyOf(kinds));
    }
}
