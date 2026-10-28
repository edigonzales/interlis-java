package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import guru.interlis.syntax.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

class SourceMetadataContractTest {
    private static CompilationRequest input(String v,String body) {
        var s=source("memory:/source-metadata.ili","INTERLIS "+v+";\n"+body);return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String v,String body) {
        var r=CompilerEngines.create().compile(input(v,body),CancellationToken.none());return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString());
    }
    private static Symbol named(SemanticGraph g,String name) {return g.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow();}
    private static String model(String body) {return "CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"=\n"+body+"END F.";}
    private static String value(Symbol s,String key) {return s.header().metaAttributes().values().get(key).text();}

    @Test void annotationsBindToActualDeclarationKindsAndPreserveTheirExactOrigin() {
        for(String v:List.of("2.3","2.4")) {
            String text="/** Model 🦊 */\n!!@ title=Model; empty=\"\"\n"+model("DOMAIN\n!!@ label=\"Gr\\u00fcezi ; \\\"Welt\\\"\"\nCode=TEXT*10;\nUNIT\n!!@ label=Meter\nm;\nTOPIC T=\n/** Class doc */\n!!@ label=Class\nCLASS C=\n!!@ label=Attribute\nname:Code;\nEND C;END T;\n");
            var r=ok(v,text);var g=r.schema().semanticGraph();var source=r.analysis().syntaxSnapshots().values().iterator().next().source();
            assertEquals("Model",value(named(g,"F"),"title"));assertEquals("",value(named(g,"F"),"empty"));
            assertEquals("Grüezi ; \"Welt\"",value(named(g,"Code"),"label"));assertEquals("Meter",value(named(g,"m"),"label"));
            assertEquals("Class",value(named(g,"C"),"label"));assertEquals("Attribute",value(named(g,"name"),"label"));
            assertEquals("Model 🦊",named(g,"F").header().documentation().text());assertEquals("Class doc",named(g,"C").header().documentation().text());
            var origin=assertInstanceOf(Origin.DeclaredOrigin.class,named(g,"Code").header().metaAttributes().values().get("label").origin());
            assertEquals("label=\"Gr\\u00fcezi ; \\\"Welt\\\"\"",source.slice(origin.range()));assertEquals(source.revision(),origin.range().revision());
            var syntax=r.analysis().syntaxSnapshots().get(source.id());assertTrue(syntax.metadata().values().stream().anyMatch(m->m.attributes().stream().anyMatch(a->a.text().equals("Meter"))));
            assertThrows(UnsupportedOperationException.class,()->named(g,"Code").header().metaAttributes().values().clear());assertThrows(UnsupportedOperationException.class,()->syntax.metadata().clear());
            assertThrows(UnsupportedOperationException.class,()->named(g,"F").header().documentation().ranges().clear());
        }
    }
    @Test void enumElementsAndConstraintMetadataDoNotLeakToTheirNeighbours() {
        for(String v:List.of("2.3","2.4")) {
            var r=ok(v,model("DOMAIN E=(\n!!@ label=Red\nred,\n!!@ label=Group\ngroup(\n!!@ label=Child\nchild),other);TOPIC T=CLASS C=flag:BOOLEAN;\n!!@ name=RequiredFlag\nMANDATORY CONSTRAINT flag;MANDATORY CONSTRAINT DEFINED(flag);END C;END T;"));var g=r.schema().semanticGraph();
            assertEquals("Red",value(named(g,"red"),"label"));assertEquals("Group",value(named(g,"group"),"label"));assertEquals("Child",value(named(g,"group.child"),"label"));assertTrue(named(g,"other").header().metaAttributes().values().isEmpty());
            var constraints=g.symbols().stream().filter(ConstraintSymbol.class::isInstance).toList();assertEquals(1,constraints.stream().filter(s->s.header().metaAttributes().values().containsKey("name")).count());
        }
    }
    @Test void documentationAndExplanationsArePublicAndAppearInHover() {
        for(String v:List.of("2.3","2.4")) {
            var r=ok(v,model("/** First line\n * Second line */\nFUNCTION f(value:BOOLEAN):BOOLEAN // External 🦊 explanation //;TOPIC T=CLASS C=MANDATORY CONSTRAINT F.f(#true);END C;END T;"));var g=r.schema().semanticGraph();var f=assertInstanceOf(FunctionSymbol.class,named(g,"f"));
            assertEquals("First line\nSecond line",f.header().documentation().text());assertEquals("External 🦊 explanation",f.explanation().text());assertEquals(1,f.explanation().ranges().size());
            var source=r.analysis().syntaxSnapshots().values().iterator().next().source();assertEquals("// External 🦊 explanation //",source.slice(f.explanation().ranges().getFirst()));
            var hover=LanguageQueries.create().hover(r.analysis(),source.range(source.text().indexOf("F.f(#")+2,source.text().indexOf("F.f(#")+2),CancellationToken.none()).known().orElseThrow();
            assertEquals("First line\nSecond line\n\nExternal 🦊 explanation",hover.documentation());
            var function=r.analysis().syntaxSnapshots().values().iterator().next().root().models().getFirst().members().stream().filter(FunctionDeclarationSyntax.class::isInstance).map(FunctionDeclarationSyntax.class::cast).findFirst().orElseThrow();
            assertEquals(f.explanation().ranges().getFirst(),function.explanation().orElseThrow().range());
        }
    }
    @Test void explanationEndsAtNextDoubleSlashAndTrailingTokenRemainsSource() {
        for(String v:List.of("2.3","2.4")) {
            String explanation="// first line\n second ä😀 line //";
            var r=ok(v,model("FUNCTION f(value:BOOLEAN):BOOLEAN "+explanation+";TOPIC T=CLASS C=MANDATORY CONSTRAINT F.f(#true);END C;END T;"));
            var f=assertInstanceOf(FunctionSymbol.class,named(r.schema().semanticGraph(),"f"));
            assertEquals("first line\n second ä😀 line",f.explanation().text());
            assertEquals(1,f.explanation().ranges().size());
            var source=r.analysis().syntaxSnapshots().values().iterator().next().source();
            assertEquals(explanation,source.slice(f.explanation().ranges().getFirst()));
            var hover=LanguageQueries.create().hover(r.analysis(),
                    source.range(source.text().indexOf("F.f(#")+2,source.text().indexOf("F.f(#")+2),CancellationToken.none()).known().orElseThrow();
            assertTrue(hover.documentation().contains("first line\n second ä😀 line"));

            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,
                    CompilerEngines.create().compile(input(v,model("FUNCTION f(value:BOOLEAN):BOOLEAN // text // trailing;TOPIC T=CLASS C=MANDATORY CONSTRAINT F.f(#true);END C;END T;")),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.severity()==guru.interlis.diagnostics.Severity.ERROR));
        }
    }
    @Test void ordinaryCommentsStringsAndEmptyDocumentationRemainLexicalData() {
        for(String v:List.of("2.3","2.4")) {
            var r=ok(v,model("/**/\n!! ordinary @CRS=EPSG:1\n/* !!@ wrong=value */\nCLASS C=END C;\nFUNCTION f(value:TEXT):BOOLEAN // text @CRS=EPSG:2 //;TOPIC T=CLASS D=MANDATORY CONSTRAINT F.f(\"!!@ label=value\");END D;END T;"));var g=r.schema().semanticGraph();
            assertTrue(named(g,"C").header().metaAttributes().values().isEmpty());assertTrue(named(g,"C").header().documentation().isEmpty());assertEquals(1,named(g,"C").header().documentation().ranges().size());
            assertTrue(g.symbols().stream().allMatch(s->s.header().metaAttributes().values().isEmpty()));
        }
    }
    @Test void identicalAssignmentsAreAllowedAndConflictsAreSourceErrors() {
        for(String v:List.of("2.3","2.4")) {
            var r=ok(v,model("!!@label=one;label=one\nDOMAIN D=TEXT;"));assertEquals("one",value(named(r.schema().semanticGraph(),"D"),"label"));
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,model("!!@label=one;label=two\nDOMAIN D=TEXT;")),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());assertTrue(rejected.analysis().diagnostics().toString().contains("METAATTRIBUTE_CONFLICT"));
        }
    }
    @Test void malformedAssignmentsAreParsingErrorsWithoutAnInternalFailure() {
        for(String v:List.of("2.3","2.4"))for(String annotation:List.of("key","=value","key=","key=\"unterminated","key=\"\\q\"","key=\"\\uGGGG\"","key=two words","key=a,b")) {
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,model("!!@"+annotation+"\nDOMAIN D=TEXT;")),CancellationToken.none()),annotation);
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SYN-0002")));
        }
        for(String v:List.of("2.3","2.4"))ok(v,model("!!@\nDOMAIN D=TEXT;"));
    }
    @Test void viewsPreserveDeclaredMetadataAndCommentOrigins() {
        for(String v:List.of("2.3","2.4")) {
            var r=ok(v,model("TOPIC T=CLASS C=\n/** Source attribute */\n!!@ label=Value\nvalue:TEXT;END C;VIEW V PROJECTION OF C;=ALL OF C;END V;END T;"));var g=r.schema().semanticGraph();
            var view=assertInstanceOf(ViewSymbol.class,named(g,"V"));var copied=g.symbol(view.members().getFirst()).orElseThrow();assertEquals("Value",value(copied,"label"));assertEquals("Source attribute",copied.header().documentation().text());
            var original=g.symbols().stream().filter(AttributeSymbol.class::isInstance).filter(s->s.owner().equals(Optional.of(named(g,"C").id()))).findFirst().orElseThrow();
            assertEquals(original.header().documentation(),copied.header().documentation());assertEquals(original.header().metaAttributes(),copied.header().metaAttributes());
        }
    }
    @Test void importedAnnotationsAndMetadataOnlyEditsMatchFreshAnalysisAndKeepRevisions() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            var id=new SourceId(reverse?"memory:/z-base.ili":"memory:/a-base.ili");
            var use=source(reverse?"memory:/a-use.ili":"memory:/z-use.ili","INTERLIS "+v+";MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS Original;TOPIC T=CLASS C=value:Original.Code;END C;END T;END F.");
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String annotation:List.of("label=first","label=first;label=conflict","label=last")) {
                var base=new SourceText(id,new SourceRevision(++revision),"INTERLIS "+v+";MODEL Original AT \"https://example.org\" VERSION \"1\"=\n/** Domain documentation */\n!!@"+annotation+"\nDOMAIN Code=TEXT;END Original.");
                var input=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Original",base,parse(base))),CompilerOptions.defaults());
                var fresh=engine.analyze(input,CancellationToken.none());var reused=previous==null?fresh:engine.analyze(input,new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(annotation.contains("conflict"),fresh.diagnostics().toString().contains("METAATTRIBUTE_CONFLICT"));
                var a=named(fresh.semanticGraph(),"Code");var b=named(reused.semanticGraph(),"Code");assertEquals(a.header().metaAttributes(),b.header().metaAttributes());assertEquals(a.header().documentation(),b.header().documentation());
                var origin=(Origin.DeclaredOrigin)b.header().metaAttributes().values().get("label").origin();assertEquals(base.revision(),origin.range().revision());assertEquals(base.id(),origin.range().source());
                if(previous!=null)assertNotEquals(previous.fingerprint(),reused.fingerprint());previous=reused;
            }
        }
    }
    @Test void swappedCrsAssignmentsDoNotBecomeEqualThroughAWholeModelMultiset() {
        for(String v:List.of("2.3","2.4")) {
            String body="MODEL Original AT \"https://example.org\" VERSION \"1\"=\n!!@CRS=EPSG:2056\nDOMAIN P=COORD 0..10,0..10;\n!!@CRS=EPSG:21781\nDOMAIN Q=COORD 0..10,0..10;END Original. MODEL F AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Original[\"1\"]=\n!!@CRS=EPSG:21781\nDOMAIN A=COORD 0..10,0..10;\n!!@CRS=EPSG:2056\nDOMAIN B=COORD 0..10,0..10;END F.";
            var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,body),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),r.rejection().reasons());assertEquals(2,r.analysis().diagnostics().stream().filter(d->d.arguments().toString().contains("TRANSLATION_COORDINATE")).count());
        }
    }
    @Test void translatedFunctionExplanationPresenceIsPartOfItsStructure() {
        for(String v:List.of("2.3","2.4")) {
            var text=translated("","","original","translated").replace(" // translated //","");
            var r=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,text),CancellationToken.none()));
            assertTrue(r.analysis().diagnostics().toString().contains("TRANSLATION_FUNCTION"));
            // An explicitly empty explanation is still a present grammar element.
            ok(v,translated("","","","translated"));
        }
    }
    private static String translated(String sourceMeta,String targetMeta,String originalDescription,String translatedDescription) {
        return "CONTRACTED MODEL Original AT \"https://example.org\" VERSION \"1\"=\n"+sourceMeta+"DOMAIN P=COORD 0..10,0..10;FUNCTION f(value:BOOLEAN):BOOLEAN // "+originalDescription+" //;END Original. CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" TRANSLATION OF Original[\"1\"]=\n"+targetMeta+"DOMAIN Q=COORD 0..10,0..10;FUNCTION g(value:BOOLEAN):BOOLEAN // "+translatedDescription+" //;END F.";
    }
    @Test void translationCrsChecksActualDeclarationMetadataNotUnrelatedSourceText() {
        for(String v:List.of("2.3","2.4")) {
            ok(v,translated("!!@ CRS=EPSG:2056; label=Original\n","!!@ CRS=EPSG:2056; label=Übersetzung\n","source @CRS=EPSG:1","translated @CRS=EPSG:2"));
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,translated("!!@ CRS=EPSG:2056\n","!!@ CRS=EPSG:21781\n","source","translated")),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION_COORDINATE"));
        }
    }
}
