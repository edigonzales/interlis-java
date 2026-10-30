package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.format.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class P3FormatterPolicyContractTest {
    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/formatter-policy.ili"), new SourceRevision(1), text);
    }
    private static SyntaxSnapshot parse(String text) {
        return SyntaxServices.create().parse(source(text), ParseOptions.defaults(ParseMode.STRICT), CancellationToken.none());
    }
    private static FormatOutcome.Formatted format(String text, FormatOptions.LineEnding ending) {
        return assertInstanceOf(FormatOutcome.Formatted.class, ModelFormatters.create().format(source(text),
                new FormatOptions(2, ending, true), CancellationToken.none()));
    }

    @Test void explicitLineEndingsNormalizeOrdinaryMultilineCommentsAndRemainIdempotent() {
        for (String version : List.of("2.3", "2.4")) {
            String input = "INTERLIS " + version + ";\nMODEL F AT \"https://example.org\" VERSION \"1\" =\n"
                    + "/* comment 🦊\n  continuation\n end */\nEND F.\n";
            for (var ending : List.of(FormatOptions.LineEnding.LF, FormatOptions.LineEnding.CRLF)) {
                String expectedEnding = ending == FormatOptions.LineEnding.LF ? "\n" : "\r\n";
                var result = format(input, ending);
                assertFalse(result.text().replace(expectedEnding, "").contains("\n"));
                assertFalse(result.text().replace(expectedEnding, "").contains("\r"));
                assertEquals(result.text(), format(result.text(), ending).text());
                assertTrue(parse(result.text()).diagnostics().isEmpty());
                assertEquals(input.replace("\r\n", "\n"), result.text().replace("\r\n", "\n"));
            }
        }
    }

    @Test void requestedWhitespaceNormalizationCannotChangeMultilineMetadataValues() {
        for (String ending : List.of("\n", "\r\n")) {
            String input = String.join(ending, "INTERLIS 2.4;",
                    "MODEL F AT \"https://example.org\" VERSION \"1\" =",
                    "!!@ fn.return=\"First line", "!!@ second line\"",
                    "FUNCTION f(value: TEXT): TEXT;", "END F.", "");
            var requested = ending.equals("\n") ? FormatOptions.LineEnding.CRLF : FormatOptions.LineEnding.LF;
            var original = parse(input);
            assertTrue(original.diagnostics().isEmpty(), original.diagnostics().toString());
            var result = format(input, requested);
            var parsed = parse(result.text());
            assertTrue(parsed.diagnostics().isEmpty(), parsed.diagnostics().toString());
            var values = parsed.metadata().values().stream().flatMap(m -> m.attributes().stream())
                    .map(SourceMetadata.Attribute::text).toList();
            assertEquals(List.of("First line" + ending + " second line"), values);
            assertTrue(result.notices().stream().anyMatch(n -> n.code().equals("VALUE_LINE_ENDING_PRESERVED")));
            assertEquals(result.text(), format(result.text(), requested).text());
        }
    }
}
