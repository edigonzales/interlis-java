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
