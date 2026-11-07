package guru.interlis.cli;

import guru.interlis.diagnostics.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticSnippetRendererTest {
    private static final SourceId ID = new SourceId("memory:/frame.ili");
    private static final SourceRevision REV = new SourceRevision(1);
    private final ConsoleDiagnosticRenderer renderer = new ConsoleDiagnosticRenderer();

    @Test void asciiTabsCrlfAndInsertionPointsUseDisplayedColumns() {
        var source = source("before\r\n\tvalue Missing;\r\nafter");
        int start = source.text().indexOf("Missing");
        String text = render(source, start, start + 7, false);
        assertTrue(text.contains("  2 |     value Missing;\n    |           ^~~~~~~"), text);
        assertTrue(text.contains(":2:8:")); // canonical UTF-16, not tab-expanded
        assertTrue(text.contains("  1 | before")); assertTrue(text.contains("  3 | after"));
        String empty = render(source, start, start, false);
        assertTrue(empty.contains("    |           ^\n"), empty);
        var eol = source("abc");
        assertTrue(render(eol,3,3,false).contains("|    ^"));
        assertTrue(render(source(""),0,0,false).contains("| ^"));
    }

    @Test void uncertainUnicodeNeverClaimsAnExactCaret() {
        for (var prefix : List.of("\t🦊e\u0301", "界", "é", "a\u200d")) {
            var source = source(prefix+" Missing\r\nnext"); int start=source.text().indexOf("Missing");
            var text=render(source,start,start+7,false);
            assertTrue(text.contains("marker omitted"),text); assertFalse(text.contains("^"),text);
            assertTrue(text.contains("[UTF-16 "+(start+1)+";"),text);
        }
        assertTrue(render(source("\ud800 Missing"),2,9,false).contains("\\ud800 Missing"));
        // A split surrogate range must not claim a position inside a display glyph.
        assertTrue(render(source("🦊Missing"),1,2,false).contains("marker omitted"));
        assertTrue(render(source("abc 🦊"),0,3,false).contains("^~~"));
    }

    @Test void controlsFromSourceUriAndArgumentsAreEscapedBeforeStyling() {
        String attacks="\u001b[2J\u001b]8;;https://bad\u0007link\u001b]8;;\u0007\u0000\u0085\u009b\u202e\u2066\u2069";
        var id=new SourceId("memory:/"+attacks+".ili");
        var source=new SourceText(id,REV,"\t"+attacks+" Missing\r\nnext");
        int offset=source.text().indexOf("Missing");
        var diagnostic=diagnostic(id,REV,offset,offset+7,Severity.ERROR,attacks);
        var plain=renderer.render(diagnostic,Map.of(id,source),DiagnosticLanguage.DE,RenderOptions.defaults(false));
        var ansi=renderer.render(diagnostic,Map.of(id,source),DiagnosticLanguage.DE,RenderOptions.defaults(true));
        assertEquals(plain,ColorOutputContractTest.stripSgr(ansi));
        assertTrue(plain.contains("\\u001b[2J")); assertTrue(plain.contains("\\u0085"));
        assertTrue(plain.contains("\\u202e")); assertTrue(plain.contains("\\u2066"));
        assertFalse(plain.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'));
        assertFalse(plain.contains("\u202e")); assertFalse(plain.contains("\u2066"));
    }

    @Test void everySeverityLanguageAndLegacyOverloadPreservesPlainProjection() {
        var source=source("one\nMissing\nthree");
        for (var severity : Severity.values()) for (var language : DiagnosticLanguage.values()) {
            var d=diagnostic(ID,REV,4,11,severity,"Missing");
            String plain=renderer.render(d,Map.of(ID,source),language,RenderOptions.defaults(false));
            String ansi=renderer.render(d,Map.of(ID,source),language,RenderOptions.defaults(true));
            assertEquals(plain,ColorOutputContractTest.stripSgr(ansi));
            assertTrue(ansi.contains("\u001b[36m<memory:/frame.ili>:2:1\u001b[0m"));
            assertTrue(ansi.contains("\u001b[90mILI-SEM-0001\u001b[0m"));
            assertTrue(ansi.contains("^~~~~~~\u001b[0m"));
            assertEquals(plain,renderer.render(d,Map.of(ID,source),language));
        }
    }

    @Test void escapedControlWindowsKeepTheActualErrorVisible() {
        var source=source("\u001b".repeat(10_000)+"Missing");
        var text=render(source,10_000,10_007,false);
        assertTrue(text.contains("Missing"),text);
        assertTrue(text.contains("^~~~~~~"),text);
        assertFalse(text.contains("\u001b"));
        text.lines().filter(line->line.contains(" | ")).forEach(line->assertTrue(line.length()<=160));
        var crlf=source("abc\r\nnext");
        assertTrue(render(crlf,4,4,false).contains("marker omitted"));
    }

    @Test void staleMissingForeignAndInvalidRangesCannotInventFrames() {
        var d=diagnostic(ID,REV,0,7,Severity.ERROR,"Missing");
        for (var map : List.of(Map.<SourceId,SourceText>of(),Map.of(ID,new SourceText(ID,new SourceRevision(2),"Missing")),
                Map.of(ID,source("short")),Map.of(ID,new SourceText(new SourceId("memory:/other"),REV,"Missing")))) {
            var text=renderer.render(d,map,DiagnosticLanguage.EN,RenderOptions.defaults(true));
            assertFalse(text.contains(" | ")); assertTrue(text.contains("@1 (UTF-16 0..7)"));
        }
        var noRange=new Diagnostic(new DiagnosticId("no-range"),new DiagnosticCode("ILI-SEM-0001"),Severity.ERROR,Phase.BINDING,
                "diagnostic.semantics.unresolved",List.of(new DiagnosticArgument.IdentifierArgument("name","Missing")),Optional.empty(),List.of(),Optional.empty(),List.of(),Optional.empty(),Optional.empty(),List.of());
        assertFalse(renderer.render(noRange,Map.of(ID,source("Missing"))).contains(" | "));
    }

    @Test void boundsAndMultilineRangesRemainSmallEvenForHugeLines() {
        assertTimeout(Duration.ofSeconds(3),()-> {
            var source=source("a".repeat(1_000_000)+"Missing"+"z".repeat(1_000_000)+"\r\nline2\nline3\nline4");
            var text=render(source,1_000_000,2_000_010,false);
            assertTrue(text.contains("...")); assertTrue(text.contains("^~~~~~~"));
            assertTrue(text.contains("[span continues]")); assertTrue(text.length()<1000);
            assertEquals(2,text.lines().filter(s->s.matches("  [0-9]+ \\| .*" )).count());
            text.lines().filter(s->s.contains(" | ")||s.startsWith("    |")).forEach(s->assertTrue(s.length()<=160,s));
        });
        var source=source("界".repeat(1000));
        var text=renderer.render(diagnostic(ID,REV,500,501,Severity.ERROR,"Missing"),Map.of(ID,source),DiagnosticLanguage.EN,new RenderOptions(false,0,0,24));
        text.lines().filter(s->s.contains(" | ")||s.startsWith("    |")).forEach(s->assertTrue(s.codePointCount(0,s.length())*2<=48,s));
        assertThrows(IllegalArgumentException.class,()->new RenderOptions(false,2,1,160));
        assertThrows(IllegalArgumentException.class,()->new RenderOptions(false,1,1,161));
    }

    private static SourceText source(String text) { return new SourceText(ID,REV,text); }
    private String render(SourceText source,int start,int end,boolean ansi) {
        return renderer.render(diagnostic(ID,REV,start,end,Severity.ERROR,"Missing"),Map.of(ID,source),DiagnosticLanguage.EN,RenderOptions.defaults(ansi));
    }
    private static Diagnostic diagnostic(SourceId id,SourceRevision rev,int start,int end,Severity severity,String name) {
        return new Diagnostic(new DiagnosticId("frame"),new DiagnosticCode("ILI-SEM-0001"),severity,Phase.BINDING,
                "diagnostic.semantics.unresolved",List.of(new DiagnosticArgument.IdentifierArgument("name",name)),Optional.of(new SourceRange(id,rev,start,end)),List.of(),Optional.empty(),List.of(),Optional.empty(),Optional.empty(),List.of());
    }
}
