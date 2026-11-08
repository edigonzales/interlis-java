package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FragmentSyntaxContractTest {
    @Test void pathsRetainAbsoluteUtf16OffsetsAfterUnicodeAndCrLfPrefixes() {
        for(var version:LanguageVersion.values())for(String prefix:List.of("before ","!! 🥳\r\n  ","😀\rprefix\r\n")) {
            String path="alpha->beta[1]";var source=new SourceText(new SourceId("memory:/fragment.ili"),new SourceRevision(7),prefix+path+" suffix");
            var range=source.range(prefix.length(),prefix.length()+path.length());
            var parsed=SyntaxServices.create().parseFragment(source,range,FragmentKind.OBJECT_PATH,version,CancellationToken.none());
            assertTrue(parsed.diagnostics().isEmpty(),parsed.diagnostics().toString());assertTrue(parsed.consumedEntireFragment());
            var syntax=assertInstanceOf(PathExpressionSyntax.class,parsed.root());assertEquals(range,syntax.range());
            assertEquals(List.of("alpha","beta"),syntax.segments().stream().map(s->source.slice(s.name().range())).toList());
            assertEquals(source.revision(),syntax.segments().getLast().name().range().revision());
            assertTrue(syntax.segments().getLast().index().isPresent());
        }
    }
    @Test void numericSignsAndOperatorsKeepTheirOriginalOffsetsAndPrecedence() {
        for (String expression : List.of("n-1", "n -1", "n- 1", "n - 1", "n/*c*/-/*d*/1", "n--1", "n+1", "n+-1")) {
            var syntax = expressionFragment(expression, LanguageVersion.ILI24);
            assertTrue(syntax.diagnostics().isEmpty(), syntax.diagnostics()::toString);
            assertTrue(syntax.consumedEntireFragment());
            var binary = assertInstanceOf(BinaryExpressionSyntax.class, syntax.root());
            var expected = expression.contains("+") ? guru.interlis.model.BinaryOperator.PLUS
                    : guru.interlis.model.BinaryOperator.MINUS;
            assertEquals(expected, binary.operator());
            assertInstanceOf(PathExpressionSyntax.class, binary.left());
            var literal = assertInstanceOf(LiteralExpressionSyntax.class, binary.right());
            String lexeme = expression.endsWith("--1") || expression.endsWith("+-1") ? "-1" : "1";
            assertEquals(lexeme, literal.lexeme());
            assertEquals(lexeme, syntax.source().slice(literal.range()));
            assertEquals(new java.math.BigDecimal(lexeme), literal.decimal().orElseThrow().exactValue());
        }
        var callSyntax = expressionFragment("positive(n-1)", LanguageVersion.ILI24);
        assertTrue(callSyntax.diagnostics().isEmpty(), callSyntax.diagnostics()::toString);
        var call = assertInstanceOf(FunctionCallExpressionSyntax.class, callSyntax.root());
        assertEquals(guru.interlis.model.BinaryOperator.MINUS,
                assertInstanceOf(BinaryExpressionSyntax.class, call.arguments().getFirst()).operator());
        var version = expressionFragment("n-1", LanguageVersion.ILI23);
        var diagnostic = version.diagnostics().stream().filter(d -> d.code().value().equals("ILI-SYN-0006"))
                .findFirst().orElseThrow();
        assertEquals("-", version.source().slice(diagnostic.primaryRange().orElseThrow()));
        for (String expression : List.of("+ 7", "n + -/*c*/1", "n +", "1e-")) {
            assertFalse(expressionFragment(expression, LanguageVersion.ILI24).diagnostics().isEmpty(), expression);
        }
        var subtraction = assertInstanceOf(BinaryExpressionSyntax.class,
                expressionFragment("a-b-c", LanguageVersion.ILI24).root());
        assertEquals(guru.interlis.model.BinaryOperator.MINUS,
                assertInstanceOf(BinaryExpressionSyntax.class, subtraction.left()).operator());
        var addition = assertInstanceOf(BinaryExpressionSyntax.class,
                expressionFragment("a+b*c", LanguageVersion.ILI24).root());
        assertEquals(guru.interlis.model.BinaryOperator.MUL,
                assertInstanceOf(BinaryExpressionSyntax.class, addition.right()).operator());
        var comparison = assertInstanceOf(BinaryExpressionSyntax.class,
                expressionFragment("a+b>c", LanguageVersion.ILI24).root());
        assertEquals(guru.interlis.model.BinaryOperator.PLUS, comparison.operator());
        assertEquals(guru.interlis.model.BinaryOperator.GT,
                assertInstanceOf(BinaryExpressionSyntax.class, comparison.right()).operator());
    }

    private static FragmentSnapshot expressionFragment(String expression, LanguageVersion version) {
        String prefix = "!! 😀\r\n";
        var source = new SourceText(new SourceId("memory:/operator-fragment.ili"), new SourceRevision(7), prefix + expression);
        return SyntaxServices.create().parseFragment(source, source.range(prefix.length(), source.text().length()),
                FragmentKind.EXPRESSION, version, CancellationToken.none());
    }

    @Test void fragmentLexerFailuresCannotDisappearAndKeepTheirOriginalLocation() {
        for(String prefix:List.of("prefix ","😀\r\n")) {
            String path="alpha$";var source=new SourceText(new SourceId("memory:/fragment-error.ili"),new SourceRevision(9),prefix+path+" suffix");
            var parsed=SyntaxServices.create().parseFragment(source,source.range(prefix.length(),prefix.length()+path.length()),FragmentKind.OBJECT_PATH,LanguageVersion.ILI24,CancellationToken.none());
            var lexical=parsed.diagnostics().stream().filter(d->d.code().value().equals("ILI-SYN-0001")).findFirst().orElseThrow();
            assertEquals("$",source.slice(lexical.primaryRange().orElseThrow()));assertEquals(source.revision(),lexical.primaryRange().orElseThrow().revision());
        }
    }
    @Test void missingFragmentTokensPointToTheFragmentEnd() {
        var source=new SourceText(new SourceId("memory:/fragment-eof.ili"),new SourceRevision(3),"😀\r\n DEFINED(alpha remainder");
        int start=source.text().indexOf("DEFINED"),end=start+"DEFINED(alpha".length();
        var parsed=assertDoesNotThrow(()->SyntaxServices.create().parseFragment(source,source.range(start,end),FragmentKind.EXPRESSION,LanguageVersion.ILI24,CancellationToken.none()));
        var error=parsed.diagnostics().stream().filter(d->d.code().value().equals("ILI-SYN-0003")).findFirst().orElseThrow();
        assertEquals(source.range(end,end),error.primaryRange().orElseThrow());
    }
    @Test void wholeSourceLexerErrorsSpanTheOriginalCodePointAfterBareCr() {
        for(String invalid:List.of("$","😀")) {
            var source=new SourceText(new SourceId("memory:/lexer.ili"),new SourceRevision(1),"INTERLIS 2.4;\rMODEL F AT \"https://example.org\" VERSION \"1\"=\r"+invalid+"END F.");
            var parsed=SyntaxServices.create().parse(source,ParseOptions.defaults(ParseMode.EDITOR),CancellationToken.none());
            var lexical=parsed.diagnostics().stream().filter(d->d.code().value().equals("ILI-SYN-0001")).findFirst().orElseThrow();
            assertEquals(invalid,source.slice(lexical.primaryRange().orElseThrow()));
        }
    }
    @Test void interpretedStringPathsKeepEscapeSpansInTheOriginalRevision() {
        String literal="\"geom\\u0065try->surface\"",prefix="😀\r\n ";
        var source=new SourceText(new SourceId("memory:/literal-path.ili"),new SourceRevision(11),prefix+literal+" suffix");
        var parsed=new guru.interlis.compiler.internal.antlr.AntlrSyntaxService().parsePathLiteral(source,
                source.range(prefix.length(),prefix.length()+literal.length()),LanguageVersion.ILI24,CancellationToken.none());
        assertTrue(parsed.diagnostics().isEmpty(),parsed.diagnostics().toString());
        var path=assertInstanceOf(PathExpressionSyntax.class,parsed.root());
        assertEquals("geometry",path.segments().getFirst().name().text());
        assertEquals("geom\\u0065try",source.slice(path.segments().getFirst().name().range()));
        assertEquals("surface",source.slice(path.segments().getLast().name().range()));
        assertEquals(source.revision(),path.range().revision());
    }
    @Test void invalidDecodedPathCharactersPointToTheirCompleteEscape() {
        String literal="\"geo\\u0024\"",prefix="😀\r\n ";
        var source=new SourceText(new SourceId("memory:/literal-path-error.ili"),new SourceRevision(4),prefix+literal+" suffix");
        var parsed=new guru.interlis.compiler.internal.antlr.AntlrSyntaxService().parsePathLiteral(source,
                source.range(prefix.length(),prefix.length()+literal.length()),LanguageVersion.ILI24,CancellationToken.none());
        var lexical=parsed.diagnostics().stream().filter(d->d.code().value().equals("ILI-SYN-0001")).findFirst().orElseThrow();
        assertEquals("\\u0024",source.slice(lexical.primaryRange().orElseThrow()));
    }
}
