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
import guru.interlis.syntax.SyntaxSnapshot;
import guru.interlis.syntax.SyntaxToken;
import guru.interlis.syntax.TokenKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FormatterSoStandardTest {
    private static final Path FIXTURES = Path.of(System.getProperty("testdata.dir"), "formatter/so-standard");
    private static final CancellationToken ACTIVE = CancellationToken.none();

    @Test
    void declarationAndCommentGolden() throws Exception {
        assertGolden("declarations", FormatOptions.defaults());
    }

    @Test
    void formatsValuesAndPreservesMetadataAndExplanations() throws Exception {
        String input = Files.readString(FIXTURES.resolve("cases/values.ili"));
        var options = new FormatOptions(2, FormatOptions.LineEnding.CRLF, false);
        String output = verify(input, options);
        assertTrue(output.contains("!!@ fn.return=\"First line\n!!@ second line\""));
        assertTrue(output.contains("// explanation with END and !! //"));
        assertTrue(output.contains("FUNCTION f(x : TEXT; y : TEXT) : TEXT;"));
        assertTrue(output.contains("  DOMAIN\r\n    D = 0 .. 100;"));
        assertTrue(output.endsWith("END Values."));
    }

    @Test
    void mandatoryAndPlausibilityBreakGolden() throws Exception {
        assertGolden("expressions", new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 40));
    }

    @Test
    void flatWidthCountsCodePointsAndIncludesTheWholeConstraint() {
        String input = "INTERLIS 2.4; MODEL F AT \"x\" VERSION \"1\" = "
                + "CLASS C = MANDATORY CONSTRAINT A == \"🦊123456789\"; END C; END F.";
        String flat = "    MANDATORY CONSTRAINT A == \"🦊123456789\";";
        int width = flat.codePointCount(0, flat.length());
        assertTrue(verify(input, new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, width))
                .contains(flat));
        assertTrue(verify(input, new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, width - 1))
                .contains("    MANDATORY CONSTRAINT\n      A =="));
    }

    @Test
    void existenceUniqueAndSetBreakGolden() throws Exception {
        assertGolden("clauses", new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 40));
    }

    @Test
    void preservesAllConstraintFamiliesAndClausesInTheFlatForm() throws Exception {
        var input = Files.readString(FIXTURES.resolve("cases/constraints.ili"));
        var output = verify(input, FormatOptions.defaults());
        assertTrue(output.contains("EXISTENCE CONSTRAINT A REQUIRED IN Entry : B OR Entry : A;"));
        assertTrue(output.contains("UNIQUE (BASKET) Named : WHERE DEFINED(A) : A, B;"));
        assertTrue(output.contains("UNIQUE (LOCAL) Parts : Key;"));
        assertTrue(output.contains("SET CONSTRAINT (BASKET) First : WHERE DEFINED(A) : DEFINED(B);"));
        assertTrue(output.contains("SET CONSTRAINT WHERE DEFINED(B) : Second : DEFINED(A);"));
    }

    @Test
    void preservesCommentsWithinEveryClauseFamily() throws Exception {
        var input = Files.readString(FIXTURES.resolve("cases/clauses.ili"))
                .replace("REQUIRED IN", "REQUIRED !! requirement\nIN")
                .replace("FirstKey, SecondKey, ThirdKey", "FirstKey, !! key\nSecondKey, ThirdKey")
                .replace("Subdetails: FirstKey", "Subdetails: /* field list */ FirstKey")
                .replace("Second: DEFINED", "Second: !! condition\nDEFINED");
        var output = verify(input, new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 40));
        assertTrue(output.contains("REQUIRED !! requirement\n"));
        assertTrue(output.contains("FirstKey, !! key\n"));
        assertTrue(output.contains(": /* field list */\n        FirstKey"), output);
        assertTrue(output.contains("Second : !! condition\n"));
    }

    @Test
    void preservesSpecialFactorsAndOriginalPathSpansInNestedArguments() {
        String input = "INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\" = CLASS C = "
                + "MANDATORY CONSTRAINT Functions.Check(ALL OF details, PARAMETER M.Name, "
                + "INSPECTION View OF detail, Functions.Nested(THIS->detail, Other[Relation]->Key)); "
                + "END C; END M.";
        String output = verify(input, new FormatOptions(2, FormatOptions.LineEnding.PRESERVE, true, 40));
        assertTrue(output.contains("ALL OF details,"));
        assertTrue(output.contains("PARAMETER M.Name,"));
        assertTrue(output.contains("INSPECTION View OF detail,"));
        assertTrue(output.contains("THIS->detail,"));
        assertTrue(output.contains("Other[Relation]->Key"));
    }

    @Test
    void coversNestedDeclarationsSectionsAndBracketRoles() throws Exception {
        assertGolden("sections", FormatOptions.defaults());
    }

    @Test
    void shiftsMixedBlockCommentAndKeepsTheFollowingToken() throws Exception {
        assertGolden("mixed-comment", FormatOptions.defaults());
    }

    static void assertGolden(String name, FormatOptions options) throws Exception {
        String input = Files.readString(FIXTURES.resolve("cases/" + name + ".ili"));
        String expected = Files.readString(FIXTURES.resolve("golden/" + name + ".ili"));
        assertEquals(expected, verify(input, options));
    }

    static String verify(String input, FormatOptions options) {
        String output = formatted(input, options);
        var before = parse(input);
        var after = parse(output);
        assertTrue(after.coverage().parseComplete() && after.coverage().consumedEntireInput()
                && after.coverage().lexicallyComplete(), after.diagnostics().toString());
        assertEquals(lexemes(before), lexemes(after));
        assertEquals(annotations(before), annotations(after));
        assertComments(before, after, options.lineEnding());
        assertEquals(output, formatted(output, options));
        return output;
    }

    private static String formatted(String input, FormatOptions options) {
        return assertInstanceOf(FormatOutcome.Formatted.class,
                ModelFormatters.create().format(source(input), options, ACTIVE)).text();
    }

    private static void assertComments(SyntaxSnapshot before, SyntaxSnapshot after,
                                       FormatOptions.LineEnding ending) {
        var original = before.tokens();
        var formatted = after.tokens();
        assertEquals(original.size(), formatted.size());
        for (int i = 0; i < original.size(); i++) {
            SyntaxToken left = original.get(i);
            if (left.channel() == 0) continue;
            SyntaxToken right = formatted.get(i);
            assertEquals(left.channel(), right.channel());
            String oldText = left.text();
            String newText = right.text();
            if (!oldText.startsWith("/*")) {
                assertEquals(oldText, newText);
                continue;
            }
            if (ending != FormatOptions.LineEnding.PRESERVE) {
                oldText = oldText.replace("\r\n", "\n").replace('\r', '\n');
                newText = newText.replace("\r\n", "\n").replace('\r', '\n');
            }
            var oldPosition = before.source().lineMap().positionAt(left.range().startOffset());
            var newPosition = after.source().lineMap().positionAt(right.range().startOffset());
            String linePrefix = before.source().text().substring(left.range().startOffset() - oldPosition.character(),
                    left.range().startOffset());
            int shift = newPosition.character() - oldPosition.character();
            if (oldText.equals(newText) && (shift == 0 || !linePrefix.isBlank())) continue;
            var oldLines = oldText.split("\r\n|\r|\n", -1);
            var newLines = newText.split("\r\n|\r|\n", -1);
            assertEquals(oldLines.length, newLines.length);
            assertEquals(oldLines[0], newLines[0]);
            for (int line = 1; line < oldLines.length; line++) {
                if (oldLines[line].isEmpty()) assertEquals("", newLines[line]);
                else if (shift >= 0) assertEquals(" ".repeat(shift) + oldLines[line], newLines[line]);
                else assertEquals(oldLines[line], " ".repeat(-shift) + newLines[line]);
            }
        }
    }

    private static List<String> lexemes(SyntaxSnapshot syntax) {
        return syntax.tokens().stream().filter(t -> t.channel() == 0 && t.kind() != TokenKind.EOF)
                .map(t -> t.kind() + ":" + t.text()).toList();
    }

    private static List<List<String>> annotations(SyntaxSnapshot syntax) {
        var result = new ArrayList<List<String>>();
        for (SyntaxToken token : syntax.tokens()) {
            var attached = syntax.metadata().get(token.range());
            if (attached == null) continue;
            var fields = new ArrayList<String>();
            fields.add(token.id().value());
            attached.attributes().forEach(value -> fields.add(value.key() + "=" + value.text()));
            attached.documentation().forEach(doc -> fields.add(syntax.tokens().stream()
                    .filter(t -> t.range().equals(doc.range())).findFirst().orElseThrow().id().value()));
            result.add(fields);
        }
        return result;
    }

    static SyntaxSnapshot parse(String text) {
        return SyntaxServices.create().parse(source(text), ParseOptions.defaults(ParseMode.STRICT), ACTIVE);
    }

    private static SourceText source(String text) {
        return new SourceText(new SourceId("memory:/so-standard.ili"), new SourceRevision(1), text);
    }
}
