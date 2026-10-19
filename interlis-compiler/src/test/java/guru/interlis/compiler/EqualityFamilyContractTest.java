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

class EqualityFamilyContractTest {
    @Test
    void invalidFormattedLiteralIsRejected() {
        SourceText source = new SourceText(new SourceId("memory:/equality.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL E AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = value : INTERLIS.XMLDate;
                      MANDATORY CONSTRAINT value == "15";
                    END A;
                  END T;
                END E.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("E",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Rejected.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }

    @Test
    void structureEqualityIsComparable() {
        assertAccepted("""
                INTERLIS 2.3;
                MODEL E AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    STRUCTURE S = END S;
                    CLASS A = s1 : S; s2 : S;
                      MANDATORY CONSTRAINT s1 == s2;
                    END A;
                  END T;
                END E.
                """);
    }

    @Test
    void formattedTextEqualityIsComparable() {
        assertAccepted("""
                INTERLIS 2.3;
                MODEL E AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    CLASS A = value : INTERLIS.XMLDate;
                      MANDATORY CONSTRAINT value == "2020-04-23";
                    END A;
                  END T;
                END E.
                """);
    }

    @Test
    void coordinateEqualityIsComparable() {
        SourceText source = new SourceText(new SourceId("memory:/equality.ili"),
                new SourceRevision(1), """
                INTERLIS 2.3;
                MODEL E AT "https://example.interlis.ch" VERSION "1.0" =
                  TOPIC T =
                    DOMAIN Point = COORD 0.000 .. 100.000, 0.000 .. 100.000;
                    CLASS A = p1 : Point; p2 : Point;
                      MANDATORY CONSTRAINT p1 == p2;
                    END A;
                  END T;
                END E.
                """);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("E",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }

    private static void assertAccepted(String text) {
        SourceText source = new SourceText(new SourceId("memory:/equality.ili"),
                new SourceRevision(1), text);
        CompilationRequest request = new CompilationRequest(List.of(new ModelSelector.ByName("E",
                Optional.empty(), Optional.empty())), SourceSet.of(Map.of(source.id(), source)),
                CompilerOptions.defaults());
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(request, CancellationToken.none()));
    }
}
