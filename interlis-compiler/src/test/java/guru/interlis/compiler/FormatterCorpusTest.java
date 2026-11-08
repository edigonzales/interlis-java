package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.format.FormatOptions;
import guru.interlis.format.FormatOutcome;
import guru.interlis.format.ModelFormatters;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormatterCorpusTest {
    @Test
    void pinnedSolothurnCorpusReparsesPreservesBindingsAndIsIdempotent() throws Exception {
        Path root = Path.of(System.getProperty("testdata.dir"), "formatter/so-standard/originals");
        for (String name : List.of(
                "ADA/SO_ADA_Denkmal_20250114.ili",
                "AFU/SO_AFU_Gewaesserraum_20260526.ili",
                "AFU/SO_AFU_Naturgefahren_20240515.ili",
                "AGI/SO_AGI_Datahub_20240403.ili",
                "AGI/SO_AGI_MOpublic_20240711.ili",
                "AVT/SO_AVT_OevKov_20260727.ili",
                "AWJF/SO_AWJF_Waldplan_20250312.ili",
                "HBA/SO_HBA_Immobilienportfolio_20260713.ili")) {
            Path original = root.resolve(name);
            assertDoesNotThrow(() -> FormatterSoStandardTest.verify(Files.readString(original),
                    FormatOptions.defaults()), name);
        }
    }

    @Test
    void rejectsLexerErrorsAndTrailingTokensWithoutAnyText() {
        String input = "INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = END M.";
        for (String suffix : List.of(" !", " Extra")) {
            var source = CompilerTestSupport.source("memory:/format-negative.ili", input + suffix);
            var rejected = assertInstanceOf(FormatOutcome.Rejected.class,
                    ModelFormatters.create().format(source, FormatOptions.defaults(), CancellationToken.none()));
            assertFalse(rejected.diagnostics().isEmpty());
        }
    }

    @Test
    void mixedLayoutEndingsRemainStableAtTheSameBoundaries() {
        String input = "INTERLIS 2.4;\r \nMODEL M AT \"x\" VERSION \"1\" =\r\n"
                + "TOPIC T =\rCLASS C = END C;\nEND T;\r\nEND M.\r";
        String output = FormatterSoStandardTest.verify(input, FormatOptions.defaults());
        assertTrue(output.startsWith("INTERLIS 2.4;\r \nMODEL M"));
        assertTrue(output.endsWith("END M.\r"));
    }

    @Test
    void normalizesADeepButAcceptedGroupAndALongFlatChain() {
        String wrapper = "INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = CLASS C = MANDATORY CONSTRAINT ";
        String chain = "DEFINED(A) AND ".repeat(500) + "DEFINED(B)";
        String nested = "NOT (".repeat(16) + chain + ")".repeat(16);
        var options = new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 80);
        String source = wrapper + nested + "; END C; END M.";
        String first = FormatterSoStandardTest.verify(source, options);
        String variant = wrapper + nested.replace(" AND ", "\n   AND  ") + "; END C; END M.";
        assertEquals(first, FormatterSoStandardTest.verify(variant, options));
    }

    @Test
    void continuationHeadersAndProtectedCommentMarginsAreStable() {
        String input = """
                INTERLIS 2.4;
                MODEL M (de)
                AT "x"
                VERSION "1"
                =
                  TOPIC T =
                            /* Table:
                row | row
                 */
                CLASS C
                EXTENDS Missing
                =
                A : TEXT;
                END C;
                END T;
                END M.
                """;
        String output = FormatterSoStandardTest.verify(input, FormatOptions.defaults());
        assertTrue(output.contains("MODEL M (de)\n  AT \"x\"\n  VERSION \"1\"\n  ="));
        assertTrue(output.contains("    CLASS C\n      EXTENDS Missing\n      ="));
        assertTrue(output.contains("            /* Table:\nrow | row\n */"));
        var source = CompilerTestSupport.source("memory:/format-comment.ili", input);
        var result = assertInstanceOf(FormatOutcome.Formatted.class,
                ModelFormatters.create().format(source, FormatOptions.defaults(), CancellationToken.none()));
        assertEquals(List.of("COMMENT_MARGIN_PRESERVED"),
                result.notices().stream().map(notice -> notice.code()).toList());
    }
}
