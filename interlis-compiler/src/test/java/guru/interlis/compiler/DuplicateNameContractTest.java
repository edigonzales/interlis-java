package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DuplicateNameContractTest {
    @Test
    void duplicateEnumerationValueIsRejected() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL D AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN Colour = (red, red);
                  END T;
                END D.
                """);
    }

    @Test
    void extendedEnumRepeatingInheritedValueIsRejected() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL D AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN Base = (red, blue);
                    DOMAIN Extended EXTENDS Base = (red);
                  END T;
                END D.
                """);
    }

    @Test
    void classAndDomainWithSameNameAreRejected() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL D AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN X = TEXT*10;
                    CLASS X = value : TEXT*10; END X;
                  END T;
                END D.
                """);
    }

    private static void assertRejected(String text) {
        SourceText source = new SourceText(new SourceId("memory:/duplicates.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("D",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
