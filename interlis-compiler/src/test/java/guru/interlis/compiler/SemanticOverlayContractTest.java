package guru.interlis.compiler;
import guru.interlis.compiler.SemanticOverlays.*;
import guru.interlis.compiler.SemanticOverlays.SemanticOverlay.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemanticOverlayContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final OverlaySource CONFIG=new OverlaySource("config:/project",10);
    private static CompilationRequest input(String v,String body,SemanticOverlay... overlays) {
        var s=CompilerTestSupport.source("memory:/overlays.ili","INTERLIS "+v+";"+body);
        var d=CompilerOptions.defaults();return CompilerTestSupport.request(List.of("F"),Map.of(s.id(),s),List.of(),
                new CompilerOptions(d.semanticProfile(),d.allowedLanguages(),d.warningPolicy(),d.budget(),new SemanticOverlays(List.of(overlays))));
    }
    private static String model(String name,String body){return "MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="+body+"END "+name+".";}
    private static CompilationOutcome.Succeeded ok(CompilationRequest in){var r=CompilerEngines.create().compile(in,TOKEN);return assertInstanceOf(CompilationOutcome.Succeeded.class,r,()->r instanceof CompilationOutcome.Rejected x?x.analysis().diagnostics().toString():r.toString());}
    private static Symbol named(SemanticGraph g,String name){return g.symbol(g.qualifiedNames().get(Target.named(name).name()).getFirst()).orElseThrow();}
    private static MetaAttributeOverlay meta(String target,String value,int priority){return new MetaAttributeOverlay(new OverlaySource("priority-"+priority,priority),Target.named(target),Map.of("label",List.of(value)));}
    @Test void metadataChangesArePublicAndPreserveBothEffectiveAndSourceOrigins() {
        for(String v:List.of("2.3","2.4")) {
            var body=model("F","!!@ label=source\nDOMAIN P=COORD 0..10,0..10;");
            var g=ok(input(v,body,meta("F.P","external",10))).schema().semanticGraph();
            var values=named(g,"F.P").header().metaAttributes();assertEquals("external",values.values().get("label").text());
            assertEquals(List.of("external","source"),values.values("label").stream().map(MetaAttributeSet.Value::text).toList());
            assertInstanceOf(Origin.OverlayOrigin.class,values.values("label").getFirst().origin());
            assertInstanceOf(Origin.DeclaredOrigin.class,values.values("label").getLast().origin());
            assertThrows(UnsupportedOperationException.class,()->values.entries().clear());
        }
    }
    @Test void externalTranslationsExtendTheCheckedClosureAndRetainTheirProvenance() {
        for(String v:List.of("2.3","2.4")) {
            var body=model("Original","DOMAIN P=COORD 0..10,0..10;")+model("F","DOMAIN Point=COORD 0..10,0..10;");
            var g=ok(input(v,body,new TranslationOverlay(CONFIG,Target.named("F"),Target.named("Original")))).schema().semanticGraph();
            var t=((ModelSymbol)named(g,"F")).translation().orElseThrow();assertEquals(named(g,"Original").id(),t.sourceTarget().orElseThrow());
            assertTrue(t.correspondences().contains(new TranslationLink(named(g,"Original.P").id(),named(g,"F.Point").id())));
            assertEquals(CONFIG.origin(),t.origin().orElseThrow());assertTrue(((ModelSymbol)named(g,"F")).imports().isEmpty());
        }
    }
    @Test void crsUsesTheExistingTranslationRuleAndRejectsNonCoordinateTargets() {
        var body=model("Original","DOMAIN P=COORD 0..10,0..10;")+model("F","DOMAIN P=COORD 0..10,0..10;").replace("VERSION \"1\"=DOMAIN P","VERSION \"1\" TRANSLATION OF Original[\"1\"]=DOMAIN P");
        var a=new CrsOverlay(CONFIG,Target.named("Original.P"),"EPSG:2056");var b=new CrsOverlay(CONFIG,Target.named("F.P"),"EPSG:2056");
        var bad=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.4",body,a),TOKEN));
        assertTrue(bad.analysis().diagnostics().toString().contains("TRANSLATION_COORDINATE"));ok(input("2.4",body,a,b));
        var wrong=CompilerEngines.create().compile(input("2.4",model("F","DOMAIN P=TEXT;"),new CrsOverlay(CONFIG,Target.named("F.P"),"EPSG:2056")),TOKEN);
        assertOverlayError(wrong);
    }
    private static void assertOverlayError(CompilationOutcome out) {
        var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,out);assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
        assertTrue(rejected.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0011")),rejected.analysis().diagnostics().toString());
    }
    @Test void missingAmbiguousAndEqualPriorityConflictsCannotPassTheGate() {
        var body=model("F","DOMAIN P=COORD 0..10,0..10;");
        assertOverlayError(CompilerEngines.create().compile(input("2.4",body,meta("Missing.P","x",1)),TOKEN));
        assertOverlayError(CompilerEngines.create().compile(input("2.4",body,meta("F.P","x",1),meta("F.P","y",1)),TOKEN));
        var duplicate=body+body.replace("VERSION \"1\"","VERSION \"2\"");
        var ambiguous=input("2.4",duplicate,meta("F.P","x",1));
        var selected=new CompilationRequest(List.of(new ModelSelector.ByName("F",Optional.of("2"),Optional.empty())),ambiguous.sources(),ambiguous.options());
        assertOverlayError(CompilerEngines.create().compile(selected,TOKEN));
        var valid=ok(input("2.4",body,meta("F.P","low",1),meta("F.P","high",2))).schema().semanticGraph();
        assertEquals("high",named(valid,"F.P").header().metaAttributes().values().get("label").text());
    }
    @Test void contentFingerprintsAreUnambiguousAndDeeplyImmutable() {
        var data=new ArrayList<>(List.of("a, b=c"));var map=new HashMap<String,List<String>>();map.put("x",data);
        var a=new MetaAttributeOverlay(CONFIG,Target.named("F.P"),map);String before=a.fingerprint();data.add("changed");map.put("y",List.of("new"));assertEquals(before,a.fingerprint());
        var b=new MetaAttributeOverlay(CONFIG,Target.named("F.P"),Map.of("x",List.of("a"),"b",List.of("c")));assertNotEquals(a.fingerprint(),b.fingerprint());
        assertEquals(a.fingerprint(),new MetaAttributeOverlay(CONFIG,Target.named("F.P"),Map.of("x",List.of("a, b=c"))).fingerprint());
        assertNotEquals(new SemanticOverlays(List.of(a,b)).fingerprint(),new SemanticOverlays(List.of(b,a)).fingerprint());
    }
    @Test void overlayOnlyEditsMatchFreshAnalysisAndDoNotMutatePublishedSnapshots() {
        var engine=CompilerEngines.create();AnalysisSnapshot old=null,previous=null;
        for(String value:List.of("first","second","first","none")) {
            var in=input("2.4",model("F","DOMAIN P=COORD 0..10,0..10;"),value.equals("none")?new SemanticOverlay[0]:new SemanticOverlay[]{meta("F.P",value,1)});
            var fresh=CompilerEngines.create().analyze(in,TOKEN);var reuse=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
            assertEquals(fresh.diagnostics(),reuse.diagnostics());assertEquals(fresh.semanticGraph().symbols().stream().toList(),reuse.semanticGraph().symbols().stream().toList());
            var metadata=named(reuse.semanticGraph(),"F.P").header().metaAttributes();assertEquals(value.equals("none")?null:value,Optional.ofNullable(metadata.values().get("label")).map(MetaAttributeSet.Value::text).orElse(null));
            if(old==null)old=reuse;assertEquals("first",named(old.semanticGraph(),"F.P").header().metaAttributes().values().get("label").text());
            if(previous!=null&&!value.equals("first"))assertNotEquals(previous.snapshotId(),reuse.snapshotId());previous=reuse;
        }
    }
    @Test void exactVersionsAndSourceHintsDisambiguateActualTargets() {
        var body=model("F","DOMAIN P=COORD 0..10,0..10;");var duplicate=body+body.replace("VERSION \"1\"","VERSION \"2\"");
        var selector=new Target(Target.named("F.P").name(),Optional.of("2"),Optional.of(new SourceId("memory:/overlays.ili")));
        var in=input("2.4",duplicate,new MetaAttributeOverlay(CONFIG,selector,Map.of("label",List.of("second"))));
        var selected=new CompilationRequest(List.of(new ModelSelector.ByName("F",Optional.of("2"),Optional.empty())),in.sources(),in.options());
        var g=ok(selected).schema().semanticGraph();assertEquals(1,g.qualifiedNames().get(Target.named("F.P").name()).size());
        assertEquals("second",named(g,"F.P").header().metaAttributes().values().get("label").text());
        var wrong=new MetaAttributeOverlay(CONFIG,new Target(selector.name(),selector.modelVersion(),Optional.of(new SourceId("memory:/wrong.ili"))),Map.of("label",List.of("wrong")));
        var missing=input("2.4",body,wrong);assertOverlayError(CompilerEngines.create().compile(missing,TOKEN));
    }
    @Test void translationConflictsAndCyclesRemainSourceErrorsRatherThanValidSchemas() {
        var body=model("Original","DOMAIN P=TEXT;")+model("Other","DOMAIN P=TEXT;")+model("F","DOMAIN P=TEXT;");
        var a=new TranslationOverlay(CONFIG,Target.named("F"),Target.named("Original"));var b=new TranslationOverlay(CONFIG,Target.named("F"),Target.named("Other"));
        var high=new TranslationOverlay(new OverlaySource("high",20),Target.named("F"),Target.named("Original"));
        assertOverlayError(CompilerEngines.create().compile(input("2.4",body,high,a,b),TOKEN));
        var cycle=new TranslationOverlay(CONFIG,Target.named("Original"),Target.named("F"));
        var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.4",body,a,cycle),TOKEN));
        assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION_CYCLE"));
        var explicit=body.replace("MODEL F AT","MODEL F AT").replace("VERSION \"1\"=DOMAIN P=TEXT;END F.","VERSION \"1\" TRANSLATION OF Original[\"1\"]=DOMAIN P=TEXT;END F.");
        assertOverlayError(CompilerEngines.create().compile(input("2.4",explicit,b),TOKEN));
    }
    @Test void metadataCannotBypassCrsTargetValidationAndProvenanceIsRevisionBound() {
        var range=new SourceRange(new SourceId("config:/external"),new SourceRevision(42),3,9);
        var source=new OverlaySource("configured",1,Optional.of(range));
        var overlay=new MetaAttributeOverlay(source,Target.named("F.P"),Map.of("CRS",List.of("EPSG:2056")));
        assertOverlayError(CompilerEngines.create().compile(input("2.4",model("F","DOMAIN P=TEXT;"),overlay),TOKEN));
        var g=ok(input("2.4",model("F","DOMAIN P=COORD 0..10,0..10;"),overlay)).schema().semanticGraph();
        assertEquals(source.origin(),named(g,"F.P").header().metaAttributes().values().get("CRS").origin());
    }
    @Test void overlayBindingRespectsCancellationAndTheSemanticWorkBudget() {
        var entries=new ArrayList<SemanticOverlay>();for(int i=0;i<100;i++)entries.add(meta("F.P","value"+i,i));
        var in=input("2.4",model("F","DOMAIN P=COORD 0..10,0..10;"),entries.toArray(SemanticOverlay[]::new));var d=in.options();
        var small=new CompilerOptions(d.semanticProfile(),d.allowedLanguages(),d.warningPolicy(),d.budget().withMaxSemanticWorkUnits(20),d.overlays());
        var cancelled=assertInstanceOf(CompilationOutcome.Cancelled.class,CompilerEngines.create().compile(new CompilationRequest(in.roots(),in.sources(),small),TOKEN));
        assertEquals(guru.interlis.execution.CancellationReason.RESOURCE_LIMIT,cancelled.reason());
        assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(in,TOKEN));
        assertInstanceOf(CompilationOutcome.Cancelled.class,CompilerEngines.create().compile(in,CancellationToken.requested(guru.interlis.execution.CancellationReason.USER_REQUESTED)));
    }

    @Test void missingAndAmbiguousConfiguredOriginalsRemainResolutionRejections() {
        var overlay=new TranslationOverlay(CONFIG,Target.named("F"),Target.named("Original"));
        var missing=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.4",model("F","DOMAIN P=TEXT;"),overlay),TOKEN));
        assertEquals(Set.of(RejectionReason.MISSING_INPUT),missing.rejection().reasons());
        assertEquals("ILI-RES-0005",missing.analysis().diagnostics().getFirst().code().value());
        var original=model("Original","DOMAIN P=TEXT;");
        var body=model("F","DOMAIN P=TEXT;")+original+original.replace("VERSION \"1\"","VERSION \"2\"");
        var ambiguous=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input("2.4",body,overlay),TOKEN));
        assertEquals(Set.of(RejectionReason.AMBIGUOUS_INPUT),ambiguous.rejection().reasons());
        assertTrue(ambiguous.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-RES-0006")));
    }

    @Test void semanticFingerprintsIgnoreReuseWhileGraphReferencesRemainSnapshotBound() {
        var engine=CompilerEngines.create();var old=engine.analyze(input("2.4",model("F","DOMAIN Gone=TEXT;")),TOKEN);
        var base=input("2.4",model("F","DOMAIN P=COORD 0..10,0..10;"),meta("F.P","new",1));
        var text=base.sources().sources().values().iterator().next();var changed=new SourceText(text.id(),new SourceRevision(2),text.text());
        var in=CompilerTestSupport.request(List.of("F"),Map.of(changed.id(),changed),List.of(),base.options());
        var fresh=ok(in);var reused=assertInstanceOf(CompilationOutcome.Succeeded.class,engine.compile(in,new AnalysisReuse(Optional.of(old),old.syntaxSnapshots()),TOKEN));
        var a=named(fresh.schema().semanticGraph(),"F.P");var b=named(reused.schema().semanticGraph(),"F.P");
        assertNotEquals(a.id(),b.id());assertEquals(fresh.analysis().fingerprint(),reused.analysis().fingerprint());
        assertEquals(fresh.schema().fingerprint(),reused.schema().fingerprint());assertNotEquals(fresh.schema().snapshotId(),reused.schema().snapshotId());
        assertThrows(guru.interlis.model.SnapshotMismatchException.class,()->fresh.schema().queries().symbol(new SymbolRef(reused.schema().snapshotId(),b.id())));
    }

}
