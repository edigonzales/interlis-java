package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.format.FormatOptions;
import guru.interlis.format.FormatOutcome;
import guru.interlis.format.ModelFormatters;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatterContractTest {
    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/format.ili"), new SourceRevision(1), text);
    }

    private static String format(String text, FormatOptions options) {
        FormatOutcome outcome = ModelFormatters.create().format(source(text), options,
                CancellationToken.none());
        return assertInstanceOf(FormatOutcome.Formatted.class, outcome).text();
    }

    private static List<String> tokenTexts(String text) {
        return SyntaxServices.create().parse(source(text),
                        ParseOptions.defaults(ParseMode.STRICT), CancellationToken.none())
                .tokens().stream().map(token -> token.text()).toList();
    }

    @Test
    void formatsIndentationAndPreservesCommentsAndUnicode() {
        String input = """
                INTERLIS 2.4;
                MODEL M AT "x" VERSION "1" =
                TOPIC T =
                CLASS C =
                value : TEXT*10; !! Grüezi 😀
                END C;
                END T;
                END M.
                """;
        String formatted = format(input, FormatOptions.defaults());
        assertTrue(formatted.contains("  TOPIC T ="));
        assertTrue(formatted.contains("    CLASS C ="));
        assertTrue(formatted.contains("      value: TEXT*10; !! Grüezi 😀"), formatted);
        assertTrue(formatted.contains("    END C;"));
        assertEquals(tokenTexts(input), tokenTexts(formatted));
    }

    @Test
    void formattingIsIdempotentAndCanUseLf() {
        String input = "INTERLIS 2.4;\r\nMODEL M AT \"x\" VERSION \"1\" =\r\nEND M.\r\n";
        FormatOptions options = new FormatOptions(2, FormatOptions.LineEnding.LF, true);
        String once = format(input, options);
        String twice = format(once, options);
        assertEquals(once, twice);
        assertFalse(once.contains("\r"));
        assertTrue(once.endsWith("\n"));
    }

    @Test
    void rejectsIncompleteSyntaxWithoutOutput() {
        FormatOutcome outcome = ModelFormatters.create().format(
                source("INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = CLASS C ="),
                FormatOptions.defaults(), CancellationToken.none());
        assertInstanceOf(FormatOutcome.Rejected.class, outcome);
    }
}
