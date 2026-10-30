package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** AC2-05/06 and cumulative cross-feature identities at the Phase 2 boundary. */
class Phase2ClosureContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static String model(String name,String original,String body) {
        return "CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\" "
                +(original==null?"":"TRANSLATION OF "+original+"[\"1\"] ")+"="+body+"END "+name+".";
    }
    private static CompilationRequest input(String version,List<String> models,boolean reverse,long revision) {
        var sources=new LinkedHashMap<SourceId,SourceText>();
        for(int i=0;i<models.size();i++) {
            var s=new SourceText(new SourceId("memory:/"+(reverse?models.size()-i:i)+".ili"),new SourceRevision(revision),
                    "INTERLIS "+version+";\r\n!! 🦊\r\n"+models.get(i));sources.put(s.id(),s);
        }
        var syntax=sources.values().stream().map(CompilerTestSupport::parse).toList();var bindings=new ArrayList<ImportBinding>();
        for(var s:syntax)for(var m:s.root().models())for(var imported:m.imports()) {
            var target=syntax.stream().flatMap(t->t.root().models().stream().map(x->Map.entry(t,x)))
                    .filter(e->e.getValue().name().equals(imported.modelName())).findFirst().orElseThrow();
            bindings.add(new ImportBinding(new ImportSite(imported.range(),m.name(),imported.modelName()),new ImportResolution.Resolved(
                    new ModelLocation(target.getKey().source().id(),target.getKey().source().revision(),target.getValue().name(),
                            Optional.of(target.getValue().modelVersion()),target.getValue().range().startOffset()))));
        }
        return CompilerTestSupport.request(List.of("F"),sources,bindings,CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest in) {
        var out=CompilerEngines.create().compile(in,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static Symbol named(SemanticGraph g,String name) {
        return g.symbol(g.qualifiedNames().get(new QualifiedName(List.of(name.split("\\.")))).getFirst()).orElseThrow();
    }
    @Test void sourceLineCoordAttributesNeedAbstractionEvenInsideCustomSegments() {
        for(String v:List.of("2.3","2.4"))for(String body:List.of(
                "TOPIC T=CLASS C=p:INTERLIS.LineCoord;END C;END T;",
                "TOPIC T(ABSTRACT)=CLASS C(ABSTRACT)=p:INTERLIS.LineCoord;END C;END T;",
                "STRUCTURE S EXTENDS INTERLIS.LineSegment=p:INTERLIS.LineCoord;END S;LINE FORM Curve:S;")) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,List.of(model("F",null,body)),false,7),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertEquals(1,out.analysis().diagnostics().size(),out.analysis().diagnostics().toString());var diagnostic=out.analysis().diagnostics().getFirst();
            assertEquals("ILI-SEM-0010",diagnostic.code().value());assertTrue(diagnostic.arguments().toString().contains("NUMERIC_ABSTRACT"));
            assertEquals(new SourceRevision(7),diagnostic.primaryRange().orElseThrow().revision());
            var attribute=(AttributeSymbol)named(out.analysis().semanticGraph(),body.startsWith("TOPIC")?"F.T.C.p":"F.S.p");
            var graph=out.analysis().semanticGraph();
            assertEquals(named(graph,"INTERLIS.LineCoord").id(),
                    ((DomainReferenceType)graph.type(attribute.declaredType()).orElseThrow()).targetDomain());
        }
    }
    @Test void abstractLineCoordFieldsAndExternalParametersStayLegalAndCanonical() {
        for(String v:List.of("2.3","2.4")) {
            var body="PARAMETER runtimePoint:INTERLIS.LineCoord;STRUCTURE S(ABSTRACT) EXTENDS INTERLIS.LineSegment=p(ABSTRACT):INTERLIS.LineCoord;END S;"
                    +"LINE FORM Curve:S;DOMAIN P=COORD 0.00..10.00,0.00..10.00;TOPIC T=CLASS A=line:POLYLINE WITH(Curve) VERTEX P;END A;"
                    +"VIEW V INSPECTION OF seg~A->line;=point:=seg->Segments[FIRST]->SegmentEndPoint;END V;END T;";
            var out=ok(input(v,List.of(model("F",null,body)),false,8));var g=out.schema().semanticGraph();
            var source=(AttributeSymbol)named(g,"F.S.p");assertTrue(source.header().has(Modifier.ABSTRACT));
            assertEquals(named(g,"INTERLIS.LineCoord").id(),((DomainReferenceType)g.type(source.declaredType()).orElseThrow()).targetDomain());
            var point=(AttributeSymbol)named(g,"F.T.V.point");
            assertEquals(g.type(((DomainSymbol)named(g,"F.P")).typeId()),g.type(point.declaredType()));
            var form=(LineFormSymbol)named(g,"F.Curve");assertEquals(named(g,"F.S").id(),form.segmentStructure().orElseThrow());
            assertTrue(g.types().stream().noneMatch(t->t instanceof ErrorType||t instanceof PendingType));
        }
    }
    @Test void translatedAssociationAllOfRolesKeepOrderedCorrespondencesAcrossImportsAndRevisions() {
        String original="TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=flag:BOOLEAN;END A;CLASS B=name:TEXT;END B;ASSOCIATION Link=a--{1}A;b--{*}B;label:TEXT;END Link;END T;";
        String translated="TOPIC U=OID AS INTERLIS.UUIDOID;CLASS X=visible:BOOLEAN;END X;CLASS Y=title:TEXT;END Y;ASSOCIATION Relation=x--{1}X;y--{*}Y;text:TEXT;END Relation;END U;";
        for(boolean reverse:List.of(false,true)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;SemanticGraph old=null;List<TranslationLink> oldLinks=null;
            for(int revision=1;revision<=3;revision++) {
                var ms=List.of(model("Original",null,original),model("Translated","Original",revision==2?translated.replace("y--{*}Y","y--{*}X"):translated),
                        model("Base",null,"IMPORTS Original;TOPIC T=DEPENDS ON Original.T;VIEW V PROJECTION OF rel~Original.T.Link;=ALL OF rel;END V;END T;"),
                        model("F","Base","IMPORTS Translated;TOPIC U=DEPENDS ON Translated.U;VIEW W PROJECTION OF rel~Translated.U.Relation;=ALL OF rel;END W;END U;")
,
                        model("Noise",null,"TOPIC U=CLASS Y=title:BOOLEAN;END Y;END U;"));
                var in=input("2.4",ms,reverse,revision);var fresh=CompilerEngines.create().analyze(in,TOKEN);
                var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(revision!=2,reused.diagnostics().isEmpty(),reused.diagnostics().toString());
                if(revision==2) {
                    var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,engine.compile(in,TOKEN));
                    assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                    assertTrue(reused.diagnostics().toString().contains("TRANSLATION_REFERENCE"));
                } else {
                    var schema=ok(in).schema().semanticGraph();
                    assertTrue(schema.qualifiedNames().getOrDefault(new QualifiedName(List.of("Noise")),List.of()).isEmpty());
                }
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                var g=reused.semanticGraph();var links=((ModelSymbol)named(g,"F")).translation().orElseThrow().correspondences();
                var view=(ViewSymbol)named(g,"F.U.W");assertEquals(List.of("x","y","text"),view.members().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList());
                for(int i=0;i<2;i++) {
                    var attr=(AttributeSymbol)g.symbol(view.members().get(i)).orElseThrow();var role=g.symbol(((Origin.DerivedOrigin)attr.origin()).declaredAt()).orElseThrow();
                    assertEquals(named(g,"Translated.U.Relation").id(),role.owner().orElseThrow());
                    var type=(ObjectArgumentType)g.type(attr.declaredType()).orElseThrow();assertFalse(type.objects());
                    assertEquals(named(g,i==0||revision==2?"Translated.U.X":"Translated.U.Y").id(),type.base().orElseThrow());
                    assertTrue(links.contains(new TranslationLink(named(g,i==0?"Original.T.Link.a":"Original.T.Link.b").id(),role.id())));
                    assertTrue(links.contains(new TranslationLink(named(g,i==0?"Base.T.V.a":"Base.T.V.b").id(),attr.id())));
                }
                assertFalse(g.qualifiedNames().getOrDefault(new QualifiedName(List.of("Noise")),List.of()).isEmpty());
                if(old==null){old=g;oldLinks=links;}assertEquals(oldLinks,((ModelSymbol)named(old,"F")).translation().orElseThrow().correspondences());
                previous=reused;
            }
        }
    }
    @Test void inheritedAssociationBasesUseTheCurrentTopicParticipants() {
        for(String v:List.of("2.3","2.4")) {
            var body="TOPIC Base=OID AS INTERLIS.UUIDOID;CLASS A=flag:BOOLEAN;END A;CLASS B=name:TEXT;END B;"
                    +"ASSOCIATION Link=a--{1}A;b--{*}B;label:TEXT;END Link;END Base;TOPIC Local EXTENDS Base="
                    +"CLASS A(EXTENDED)=local:TEXT;END A;VIEW V PROJECTION OF src~Link;=ALL OF src;END V;END Local;";
            var in=input(v,List.of(model("F",null,body)),false,11);var out=ok(in);var g=out.schema().semanticGraph();
            var view=(ViewSymbol)named(g,"F.Local.V");var basis=(ViewSymbol.Base)g.symbol(view.bases().getFirst()).orElseThrow();
            assertEquals(named(g,"F.Base.Link").id(),basis.target());
            assertEquals(v.equals("2.4")?List.of("a","b","label"):List.of("label"),view.members().stream().map(id->g.symbol(id).orElseThrow().name().value()).toList());
            if(v.equals("2.4")) {
                var role=(AttributeSymbol)g.symbol(view.members().getFirst()).orElseThrow();
                assertEquals(named(g,"F.Local.A").id(),((ObjectArgumentType)g.type(role.declaredType()).orElseThrow()).base().orElseThrow());
            }
            var source=in.sources().sources().values().iterator().next();int offset=source.text().indexOf("src~Link")+4;
            assertEquals(basis.target(),LanguageQueries.create().definition(out.analysis(),source.range(offset,offset),TOKEN).known().orElseThrow().symbol().symbol());
        }
    }

    @Test void translatedGeometryMetadataAndGenericClosuresRetainTheirOwnBoundIdentities() {
        for(String v:List.of("2.3","2.4"))for(boolean reverse:List.of(false,true)) {
            String shared="TOPIC Systems=CLASS CS EXTENDS INTERLIS.COORDSYSTEM=END CS;END Systems;"
                    +"REFSYSTEM BASKET Library~Systems OBJECTS OF CS:World,Other;"
                    +"STRUCTURE Segment EXTENDS INTERLIS.LineSegment=tag:TEXT;END Segment;LINE FORM Curve:Segment;"
                    +"DOMAIN P=COORD 0.00..10.00{Library.World[1]},0.00..10.00{Library.World[2]};Q=COORD 0.00..10.00{Library.World[1]},0.00..10.00{Library.World[2]};"
                    +(v.equals("2.4")?"G(GENERIC)=COORD NUMERIC,NUMERIC;CONTEXT ctx=G=P;":"");
            String base="IMPORTS Original;DOMAIN Line=POLYLINE WITH(Original.Curve) VERTEX Original.P;TOPIC Data=CLASS C=position:Original."
                    +(v.equals("2.4")?"G":"P")+";line:Line;END C;END Data;";
            var models=List.of(model("Original",null,shared),model("Translated","Original",shared),model("Base",null,base),
                    model("F","Base",base.replace("Original.","Translated.").replace("IMPORTS Original;","IMPORTS Translated;")),
                    model("Noise",null,shared));
            var in=input(v,models,reverse,12);var out=ok(in);var g=out.schema().semanticGraph();
            var links=((ModelSymbol)named(g,"F")).translation().orElseThrow().correspondences();
            for(String suffix:List.of("Library.World","Curve","Segment","P","Q"))
                assertTrue(links.contains(new TranslationLink(named(g,"Original."+suffix).id(),named(g,"Translated."+suffix).id())));
            var coordinate=(CoordinateType)g.type(((DomainSymbol)named(g,"Translated.P")).typeId()).orElseThrow();
            for(int i=0;i<2;i++) {
                var system=coordinate.axes().get(i).referenceSystem().orElseThrow();
                assertEquals(named(g,"Translated.Library.World").id(),system.target());assertEquals(i+1,system.axis().orElseThrow());
            }
            var line=(GeometryType)g.type(((DomainSymbol)named(g,"F.Line")).typeId()).orElseThrow();
            assertEquals(List.of(named(g,"Translated.Curve").id()),line.lineForms());assertEquals(named(g,"Translated.P").id(),line.vertexDomain().orElseThrow());
            assertEquals(named(g,"Translated.Segment").id(),((LineFormSymbol)named(g,"Translated.Curve")).segmentStructure().orElseThrow());
            var topic=(TopicSymbol)named(g,"F.Data");
            if(v.equals("2.4")) {
                var binding=topic.genericBindings().getFirst();assertEquals(TopicSymbol.BindingState.CONCRETE,binding.state());
                assertEquals(named(g,"Translated.G").id(),binding.genericDomain());
                assertEquals(List.of(named(g,"Translated.P").id()),binding.alternatives());assertEquals(List.of(named(g,"Translated.ctx").id()),binding.contexts());
            } else assertTrue(topic.genericBindings().isEmpty());
            assertTrue(g.qualifiedNames().getOrDefault(new QualifiedName(List.of("Noise")),List.of()).isEmpty());
            var bad=new ArrayList<>(models);bad.set(3,models.get(3).replace("VERTEX Translated.P","VERTEX Translated.Q"));
            var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,bad,reverse,13),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
            assertTrue(rejected.analysis().diagnostics().toString().contains("TRANSLATION_REFERENCE"));
        }
    }
    @Test void equalNamedGenericImportsKeepSeparateDeferredBindingsAndRecoverFromAnUnusedChoice() {
        String shared="DOMAIN G(GENERIC)=COORD NUMERIC,NUMERIC;A=COORD 0..10,0..10;B=COORD 20..30,20..30;CONTEXT ctx=G=A OR B;";
        for(boolean reverse:List.of(false,true)) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;
            for(int step=1;step<=3;step++) {
                String body="IMPORTS West,East;TOPIC T=DEFERRED GENERICS West.G,East.G;CLASS C=p:"+(step==2?"East":"West")+".G;q:East.G;END C;END T;";
                var in=input("2.4",List.of(model("West",null,shared),model("East",null,shared),model("F",null,body)),reverse,step);
                var fresh=CompilerEngines.create().analyze(in,TOKEN);var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(step!=2,reused.diagnostics().isEmpty(),reused.diagnostics().toString());
                if(step==2)assertTrue(reused.diagnostics().toString().contains("GENERIC_DEFERRED_UNUSED"));
                else {
                    var g=ok(in).schema().semanticGraph();var topic=(TopicSymbol)named(g,"F.T");assertEquals(2,topic.genericBindings().size());
                    assertEquals(List.of(named(g,"West.G").id(),named(g,"East.G").id()),topic.deferredGenerics());
                    for(var binding:topic.genericBindings()) {
                        assertEquals(TopicSymbol.BindingState.DEFERRED,binding.state());
                        var model=g.symbol(g.symbol(binding.genericDomain()).orElseThrow().owner().orElseThrow()).orElseThrow().name().value();
                        assertEquals(List.of(named(g,model+".A").id(),named(g,model+".B").id()),binding.alternatives());
                        assertEquals(List.of(named(g,model+".ctx").id()),binding.contexts());
                    }
                }
                previous=reused;
            }
        }
    }

}
