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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntaxContractTest {
    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/syntax.ili"), new SourceRevision(1), text);
    }

    private static SyntaxSnapshot parse(String text, ParseMode mode) {
        SyntaxService service = SyntaxServices.create();
        return service.parse(source(text), ParseOptions.defaults(mode), CancellationToken.none());
    }

    @Test
    void reconstructsExactUnicodeAndComments() {
        String text = "!! Grüezi 😀\r\nINTERLIS 2.4;\r\nMODEL M AT \"x\" VERSION \"1\" =\r\n"
                + "  TOPIC T =\r\n    CLASS C =\r\n      f : TEXT*3;\r\n    END C;\r\n  END T;\r\n"
                + "END M.\r\n";
        SyntaxSnapshot snapshot = parse(text, ParseMode.EDITOR);
        assertEquals(text, snapshot.reconstruct());
        assertTrue(snapshot.consumedEntireInput());
        assertTrue(snapshot.diagnostics().stream()
                .noneMatch(d -> d.code().value().startsWith("ILI-SYN")));
        assertTrue(snapshot.trivia().stream().anyMatch(t -> t.kind()
                == guru.interlis.syntax.TriviaKind.DOCUMENTATION_COMMENT));
        assertTrue(snapshot.tokens().stream().anyMatch(t -> t.kind()
                == guru.interlis.syntax.TokenKind.EOF));
    }

    @Test
    void rejectsTrailingContentAndUnsupportedVersion() {
        SyntaxSnapshot trailing = parse("INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = "
                + "END M. GARBAGE", ParseMode.STRICT);
        assertTrue(trailing.diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SYN-0004")));
        SyntaxSnapshot old = parse("INTERLIS 1.0; MODEL M AT \"x\" VERSION \"1\" = END M.",
                ParseMode.STRICT);
        assertTrue(old.diagnostics().stream()
                .anyMatch(d -> d.code().value().equals("ILI-SYN-0005")),
                () -> old.diagnostics().toString());
    }

    @Test
    void editorRecoveryKeepsFollowingDeclarations() {
        SyntaxSnapshot snapshot = parse("INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = "
                + "TOPIC T = CLASS A = height : END A; CLASS B = name : TEXT*4; END B; "
                + "END T; END M.", ParseMode.EDITOR);
        assertFalse(snapshot.diagnostics().isEmpty());
        assertTrue(snapshot.root().models().getFirst().members().size() >= 1);
        assertTrue(snapshot.coverage().parseComplete() == false);
    }

    @Test
    void fragmentAnalysisRejectsForeignRevision() {
        SourceText source = source("INTERLIS 2.4;");
        SourceText foreign = new SourceText(source.id(), new SourceRevision(2), source.text());
        var service = SyntaxServices.create();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.parseFragment(source, foreign.range(0, 0),
                        guru.interlis.syntax.FragmentKind.EXPRESSION,
                        guru.interlis.model.LanguageVersion.ILI24, CancellationToken.none()));
    }
}
