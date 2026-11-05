package guru.interlis.format;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import guru.interlis.syntax.SyntaxSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FormatterVerifierTest {
    private static final String INPUT = """
            INTERLIS 2.4;
            MODEL M AT "x" VERSION "1" =
            !!@ name = "First
            !!@ line"
            /** Documentation. */
            CLASS C = END C;
            CLASS D = END D;
            END M.
            """;

    @Test
    void rejectsTokenAndMetadataMutations() {
        reject(INPUT.replace("CLASS C = END C;", "CLASS X = END X;"));
        reject(INPUT.replace("!!@ line", " !!@ line"));
        reject(INPUT.replace("Documentation.", "Altered."));
    }

    @Test
    void rejectsAnAnnotationMovedToAnotherDeclaration() {
        reject(INPUT.replace("/** Documentation. */\nCLASS C = END C;\n",
                "CLASS C = END C;\n/** Documentation. */\n"));
    }

    @Test
    void rejectsMovingOnlyTheOpenerOfAMultilineComment() {
        String input = INPUT.replace("/** Documentation. */", "/** Documentation.\n */");
        String output = input.replace("/** Documentation.", "  /** Documentation.");
        assertThrows(IllegalStateException.class, () -> FormatVerifier.verify(parse(input), parse(output),
                FormatOptions.defaults(), CancellationToken.none()));
    }

    private static void reject(String output) {
        assertThrows(IllegalStateException.class, () -> FormatVerifier.verify(parse(INPUT), parse(output),
                FormatOptions.defaults(), CancellationToken.none()));
    }

    private static SyntaxSnapshot parse(String text) {
        return SyntaxServices.create().parse(new SourceText(new SourceId("memory:/format-verifier.ili"),
                new SourceRevision(1), text), ParseOptions.defaults(ParseMode.STRICT), CancellationToken.none());
    }
}
