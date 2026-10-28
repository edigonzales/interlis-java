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

class GeometryConstraintContractTest {
    @Test
    void zeroOverlapIsRejected() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL G AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN Point = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                    CLASS C =
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point
                        WITHOUT OVERLAPS > 0;
                    END C;
                  END T;
                END G.
                """);
    }

    @Test
    void lineAttributesMustBeStructure() {
        assertRejected("""
                INTERLIS 2.3;
                MODEL G AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN Point = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                    CLASS Attrs = END Attrs;
                    CLASS C =
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point
                        WITHOUT OVERLAPS > 0.100 LINE ATTRIBUTES Attrs;
                    END C;
                  END T;
                END G.
                """);
    }

    private static void assertRejected(String text) {
        SourceText source = new SourceText(new SourceId("memory:/geometry-rules.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("G",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
