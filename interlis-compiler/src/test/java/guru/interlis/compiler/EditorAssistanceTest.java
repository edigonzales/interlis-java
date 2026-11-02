package guru.interlis.compiler;

import guru.interlis.compiler.query.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorAssistanceTest {
    static SourceText source(String text){return new SourceText(new SourceId("memory:/assist.ili"),new SourceRevision(1),text);}
    static AnalysisSnapshot analyze(SourceText s){return CompilerEngines.create().analyze(new CompilationRequest(List.of(new ModelSelector.BySource(s.id())),SourceSet.of(Map.of(s.id(),s)),CompilerOptions.defaults()),CancellationToken.none());}
    static String apply(SourceText s,EditorEditPlan p){String text=s.text();for(var e:p.edits().stream().sorted(Comparator.comparingInt((TextEdit e)->e.range().startOffset()).reversed()).toList())text=text.substring(0,e.range().startOffset())+e.replacement()+text.substring(e.range().endOffset());return text;}
    @Test void closesActualIncompleteBlocksAndKeepsCaretInside(){for(String header:List.of("MODEL M AT \"x\" VERSION \"1\" =","MODEL M AT \"x\" VERSION \"1\" =\nTOPIC T =","MODEL M AT \"x\" VERSION \"1\" =\nTOPIC T =\nCLASS C (ABSTRACT)\n =","MODEL M AT \"x\" VERSION \"1\" =\nSTRUCTURE S =","MODEL M AT \"x\" VERSION \"1\" =\nVIEW TOPIC V =")){var s=source("INTERLIS 2.4;\n"+header+"\n");var a=analyze(s);var p=new EditorAssistanceService().planBlockClose(a,s.range(s.length(),s.length()),new EditorIndentOptions(2,true),CancellationToken.none());assertTrue(p.isPresent(),()->header+" nodes "+a.syntaxSnapshot(s.id()).orElseThrow().root());String result=apply(s,p.orElseThrow());assertTrue(result.contains(header.contains("TOPIC V")?"DEPENDS ON ":"\n  \nEND"),result);assertTrue(p.orElseThrow().caretOffset()<result.length());assertEquals(s.revision(),p.orElseThrow().revision());}}
    @Test void commentsStringsValueAssignmentsAndExistingEndDoNotClose(){for(String body:List.of("!! CLASS C =\n","/* CLASS C =\n */\n","DOMAIN D =\n","UNIT U =\n","CLASS C =\nname: TEXT =\n","!!@ ili2db.dispName=\n","CLASS C =\n\nEND C;\nEND M.")){String text="INTERLIS 2.4;\nMODEL M AT \"x\" VERSION \"1\" =\n"+body;var s=source(text);int offset=body.contains("END C")?text.indexOf("\n\n")+1:text.length();assertTrue(new EditorAssistanceService().planBlockClose(analyze(s),s.range(offset,offset),new EditorIndentOptions(2,true),CancellationToken.none()).isEmpty(),body);}}
    @Test void quickFixEndNameAndSemicolonAreMinimalAndActuallyRepair(){for(String text:List.of("INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\"=CLASS C=END Wrong;END M.","INTERLIS 2.4; MODEL M AT \"x\" VERSION \"1\"=DOMAIN D=TEXT\nEND M.")){var s=source(text);var a=analyze(s);var p=new EditorAssistanceService().quickFixes(a,s.range(0,s.length()),CancellationToken.none());assertFalse(p.isEmpty(),()->a.diagnostics().toString());for(var plan:p){assertEquals(1,plan.edits().size());String changed=apply(s,plan);var after=analyze(source(changed));assertTrue(after.diagnostics().stream().noneMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR),()->changed+after.diagnostics());}}}
}
