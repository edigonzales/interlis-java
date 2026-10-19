package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxService;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageProfileContractTest {
    private static SyntaxSnapshot parse(String text) {
        SourceText source = new SourceText(new SourceId("memory:/profile.ili"),
                new SourceRevision(1), text);
        SyntaxService service = SyntaxServices.create();
        return service.parse(source, ParseOptions.defaults(ParseMode.STRICT),
                CancellationToken.none());
    }

    private static boolean versionDiagnostic(SyntaxSnapshot snapshot) {
        return snapshot.diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SYN-0006"));
    }

    private static final String GENERIC_MODEL = """
            INTERLIS %s;
            MODEL M AT "x" VERSION "1" =
              DOMAIN
                Coord (GENERIC) = COORD NUMERIC;
            END M.
            """;

    @Test
    void genericDomainIsRejectedInIli23AndAcceptedInIli24() {
        assertTrue(versionDiagnostic(parse(GENERIC_MODEL.formatted("2.3"))));
        assertFalse(versionDiagnostic(parse(GENERIC_MODEL.formatted("2.4"))));
    }

    @Test
    void multicoordIsRejectedInIli23AndAcceptedInIli24() {
        String model = """
                INTERLIS %s;
                MODEL M AT "x" VERSION "1" =
                  DOMAIN
                    Coord = MULTICOORD 1.0 .. 2.0, 1.0 .. 2.0;
                END M.
                """;
        assertTrue(versionDiagnostic(parse(model.formatted("2.3"))));
        assertFalse(versionDiagnostic(parse(model.formatted("2.4"))));
    }

    @Test
    void inlineEnumerationCollectionIsRejectedInIli23() {
        String model = """
                INTERLIS %s;
                MODEL M AT "x" VERSION "1" =
                  TOPIC T =
                    CLASS C =
                      values : BAG OF (a, b);
                    END C;
                  END T;
                END M.
                """;
        assertTrue(versionDiagnostic(parse(model.formatted("2.3"))));
        assertFalse(versionDiagnostic(parse(model.formatted("2.4"))));
    }
}
