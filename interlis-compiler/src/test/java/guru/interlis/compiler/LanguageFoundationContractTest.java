package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** L1-1: appendix-A geometry declarations and exact source/import binding. */
class LanguageFoundationContractTest {
    private static CompilationRequest input(String version,String imports,String body,int revision) {
        var s=new SourceText(new SourceId("memory:/foundation.ili"),new SourceRevision(revision),
                "INTERLIS "+version+";CONTRACTED MODEL M AT \"https://example.org\" VERSION \"1\"="+imports+body+"END M.");
        return request(List.of("M"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest input) {
        var out=CompilerEngines.create().compile(input,CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static StructureSymbol canonical(AnalysisSnapshot analysis,String name) {
        var ids=analysis.queries().findQualifiedName(QualifiedName.of("INTERLIS",name));
        assertEquals(1,ids.size(),name);
        return assertInstanceOf(StructureSymbol.class,analysis.queries().symbol(ids.getFirst()).known().orElseThrow());
    }
    @Test void ordinaryGeometryStructuresExposeTheNormativeMembersAndConstraint() {
        for(String v:List.of("2.3","2.4")) {
            var out=ok(input(v,"","STRUCTURE Holder=line:INTERLIS.LineGeometry;edge:INTERLIS.SurfaceEdge;border:INTERLIS.SurfaceBoundary;END Holder;",1));
            var graph=out.schema().semanticGraph();var line=canonical(out.analysis(),"LineGeometry");
            var edge=canonical(out.analysis(),"SurfaceEdge");var boundary=canonical(out.analysis(),"SurfaceBoundary");
            for(var s:List.of(line,edge,boundary)) {
                assertEquals(new Origin.PredefinedOrigin(v.equals("2.3")?LanguageVersion.ILI23:LanguageVersion.ILI24,s.name().value()),s.origin());
                assertTrue(s.header().nameRange().isEmpty());assertFalse(s.header().has(Modifier.FINAL));
                assertEquals(s.members(),graph.effectiveMembers(s.id()).stream().map(EffectiveMember::declaredSymbol).toList());
            }
            assertEquals(List.of("Segments"),line.members().stream().map(id->graph.symbol(id).orElseThrow().name().value()).toList());
            var segments=(CollectionType)graph.type(((AttributeSymbol)graph.symbol(line.members().getFirst()).orElseThrow()).declaredType()).orElseThrow();
            assertEquals(CollectionKind.LIST,segments.collectionKind());assertEquals(Cardinality.unboundedFrom(0),segments.cardinality());
            assertEquals(canonical(out.analysis(),"LineSegment").id(),((StructureValueType)graph.type(segments.elementType()).orElseThrow()).target().orElseThrow());
            assertEquals(v.equals("2.3")?List.of("Geometry","LineAttrs"):List.of("Geometry"),edge.members().stream().map(id->graph.symbol(id).orElseThrow().name().value()).toList());
            var geometry=(GeometryType)graph.type(((AttributeSymbol)graph.symbol(edge.members().getFirst()).orElseThrow()).declaredType()).orElseThrow();
            assertTrue(geometry.directed());assertEquals(GeometryKind.POLYLINE,geometry.geometryKind());assertTrue(geometry.vertexDomain().isEmpty());assertTrue(geometry.lineForms().isEmpty());
            if(v.equals("2.3"))assertEquals("ANYSTRUCTURE",((GenericType)graph.type(((AttributeSymbol)graph.symbol(edge.members().getLast()).orElseThrow()).declaredType()).orElseThrow()).name());
            var lines=(CollectionType)graph.type(((AttributeSymbol)graph.symbol(boundary.members().getFirst()).orElseThrow()).declaredType()).orElseThrow();
            assertEquals(edge.id(),((StructureValueType)graph.type(lines.elementType()).orElseThrow()).target().orElseThrow());
            assertEquals(1,line.constraints().size());var constraint=(ConstraintSymbol)graph.symbol(line.constraints().getFirst()).orElseThrow();
            var call=(FunctionCallExpression)graph.expression(((MandatoryConstraint)constraint.definition()).condition()).orElseThrow();
            assertEquals("INTERLIS.isOfClass",call.functionName());assertTrue(call.range().isEmpty());assertTrue(call.range().source().value().startsWith("predefined:/"));
            var first=(PathExpression)graph.expression(call.children().getFirst()).orElseThrow();
            assertEquals(PathIndex.Kind.FIRST,((PathStep.IndexStep)first.path().steps().getLast()).index().kind());
            assertEquals(canonical(out.analysis(),"StartSegment").id(),((SymbolValueExpression)graph.expression(call.children().getLast()).orElseThrow()).target());
        }
    }
    @Test void predefinedGeometryUsesTheActualQualifiedOrUnqualifiedSourceRange() {
        for(String v:List.of("2.3","2.4"))for(boolean bare:List.of(false,true)) {
            String spelling=(bare?"":"INTERLIS.")+"SurfaceBoundary";
            var out=ok(input(v,bare?"IMPORTS UNQUALIFIED INTERLIS;":"","STRUCTURE Holder=border:"+spelling+";END Holder;",1));
            var source=out.analysis().syntaxSnapshots().values().iterator().next().source();int start=source.text().indexOf(spelling);
            var range=source.range(start+(bare?0:9),start+(bare?0:9));
            assertEquals(canonical(out.analysis(),"SurfaceBoundary").id(),LanguageQueries.create().symbolAt(out.analysis(),range,CancellationToken.none()).known().orElseThrow().symbol());
            assertEquals(source.revision(),out.schema().semanticGraph().references().stream().filter(r->source.slice(r.range()).equals(spelling)).findFirst().orElseThrow().range().revision());
        }
    }
    @Test void absentImportWrongCaseAndUnknownGeometryNameRemainSourceErrors() {
        for(String v:List.of("2.3","2.4"))for(String name:List.of("SurfaceEdge","INTERLIS.surfaceEdge","INTERLIS.SurfaceEdges")) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,"","STRUCTURE Holder=value:"+name+";END Holder;",1),CancellationToken.none()));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0001")));
        }
    }
    @Test void importEditsMatchFreshAnalysisAndDoNotMutatePublishedDeclarations() {
        for(String v:List.of("2.3","2.4")) {
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;StructureSymbol published=null;
            for(int rev=1;rev<=3;rev++) {
                var request=input(v,rev==2?"IMPORTS INTERLIS;":"IMPORTS UNQUALIFIED INTERLIS;","STRUCTURE Holder=value:SurfaceEdge;END Holder;",rev);
                var reused=engine.analyze(request,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
                var fresh=CompilerEngines.create().analyze(request,CancellationToken.none());
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(rev!=2,reused.diagnostics().isEmpty());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().symbols(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().symbols(),reused.semanticGraph()));
                if(rev==1)published=canonical(reused,"SurfaceEdge");
                if(rev==3)assertEquals(published,canonical(reused,"SurfaceEdge"));
                previous=reused;
            }
        }
    }
    @Test void approvedTypeModelsAcceptStructuresAndAbstractClassesButRejectTopics() {
        for(String v:List.of("2.3","2.4"))for(String body:List.of("STRUCTURE S=value:TEXT;END S;","CLASS C(ABSTRACT)=value:TEXT;END C;")) {
            var request=input(v,"",body,1);var source=request.sources().sources().values().iterator().next();
            var typed=new SourceText(source.id(),source.revision(),source.text().replace("CONTRACTED MODEL","CONTRACTED TYPE MODEL"));
            ok(request(List.of("M"),Map.of(typed.id(),typed),List.of(),CompilerOptions.defaults()));
        }
        for(String v:List.of("2.3","2.4")) {
            var request=input(v,"","TOPIC T=END T;",1);var source=request.sources().sources().values().iterator().next();
            var typed=new SourceText(source.id(),source.revision(),source.text().replace("CONTRACTED MODEL","CONTRACTED TYPE MODEL"));
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(List.of("M"),Map.of(typed.id(),typed),List.of(),CompilerOptions.defaults()),CancellationToken.none()));
            assertTrue(out.analysis().diagnostics().toString().contains("TYPE_MODEL_CONTENT"));
        }
    }
    @Test void ordinaryAndContextualGeometryKeepDistinctIdentitiesAndVertexTypes() {
        for(String v:List.of("2.3","2.4")) {
            var out=ok(input(v,"","STRUCTURE Holder=value:INTERLIS.LineGeometry;END Holder;"
                    +"DOMAIN P=COORD 0.00..100.00,0.00..100.00;TOPIC T=CLASS C=line:POLYLINE WITH (STRAIGHTS) VERTEX P;END C;"
                    +"VIEW V INSPECTION OF l~C->line; = ALL OF l; END V;END T;",1));
            var graph=out.schema().semanticGraph();var canonical=canonical(out.analysis(),"LineGeometry");
            var contextual=graph.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast)
                    .filter(s->s.name().value().equals("LineGeometry")).findFirst().orElseThrow();
            assertNotEquals(canonical.id(),contextual.id());
            assertEquals(new Origin.DerivedOrigin(canonical.id(),DerivationKind.CONTEXTUALIZATION),contextual.origin());
            var segments=(CollectionType)graph.type(((AttributeSymbol)graph.symbol(contextual.members().getFirst()).orElseThrow()).declaredType()).orElseThrow();
            var segment=(StructureSymbol)graph.symbol(((StructureValueType)graph.type(segments.elementType()).orElseThrow()).target().orElseThrow()).orElseThrow();
            var endpoint=(AttributeSymbol)graph.symbol(segment.members().getFirst()).orElseThrow();
            var p=(DomainSymbol)graph.symbols().stream().filter(s->s.name().value().equals("P")).findFirst().orElseThrow();
            assertEquals(p.typeId(),endpoint.declaredType());
        }
    }
    @Test void mixedLanguageSnapshotsOwnEveryMaterializedPredefinedDeclaration() {
        for(boolean reversed:List.of(false,true))for(var roots:List.of(List.of("M23"),List.of("M24"),List.of("M23","M24"))) {
            Map<SourceId,SourceText> sources=new LinkedHashMap<>();
            for(String v:reversed?List.of("2.4","2.3"):List.of("2.3","2.4")) {
                String name=v.equals("2.3")?"M23":"M24";
                var source=new SourceText(new SourceId("memory:/"+name+".ili"),new SourceRevision(1),
                        "INTERLIS "+v+";CONTRACTED MODEL "+name+" AT \"https://example.org\" VERSION \"1\"="
                                +"IMPORTS INTERLIS;STRUCTURE H=line:INTERLIS.LineGeometry;END H;END "+name+".");
                sources.put(source.id(),source);
            }
            var request=request(roots,sources,List.of(),CompilerOptions.defaults());
            var fresh=ok(request).analysis();var engine=CompilerEngines.create();
            var reused=engine.analyze(request,new AnalysisReuse(Optional.of(fresh),fresh.syntaxSnapshots()),CancellationToken.none());
            for(var analysis:List.of(fresh,reused)) {
                var graph=analysis.semanticGraph();
                assertEquals(new HashSet<>(roots.stream().map(n->n.equals("M23")?LanguageVersion.ILI23:LanguageVersion.ILI24).toList()),graph.languageVersions());
                assertEquals(2,analysis.queries().findQualifiedName(QualifiedName.of("INTERLIS")).size());
                assertEquals(2,analysis.queries().findQualifiedName(QualifiedName.of("INTERLIS","LineGeometry")).size());
                for(var symbol:graph.symbols())if(symbol.origin() instanceof Origin.PredefinedOrigin p
                        && p.key().startsWith("INTERLIS.") && !p.key().substring(9).contains(".")) {
                    var owner=assertInstanceOf(ModelSymbol.class,graph.symbol(symbol.owner().orElseThrow()).orElseThrow());
                    assertEquals(p.language(),owner.header().languageVersion());
                    assertTrue(owner.members().contains(symbol.id()),symbol.id().toString());
                }
                for(var id:analysis.queries().findQualifiedName(QualifiedName.of("INTERLIS"))) {
                    var model=(ModelSymbol)graph.symbol(id.symbol()).orElseThrow();
                    assertFalse(model.members().isEmpty());
                    for(var member:model.members())assertEquals(Optional.of(model.id()),graph.symbol(member).orElseThrow().owner());
                }
            }
        }
    }
    @Test void predefinedModelAndExplicitImportsExposeTheVersionedCanonicalOwner() {
        for(String v:List.of("2.3","2.4")) {
            var out=ok(input(v,"IMPORTS INTERLIS;","STRUCTURE Holder=edge:INTERLIS.SurfaceEdge;id:INTERLIS.UUIDOID;END Holder;",1));
            var graph=out.schema().semanticGraph();var ids=out.analysis().queries().findQualifiedName(QualifiedName.of("INTERLIS"));
            assertEquals(1,ids.size());var model=(ModelSymbol)graph.symbol(ids.getFirst().symbol()).orElseThrow();
            assertEquals(ModelKind.TYPE,model.modelKind());assertEquals(v.equals("2.3"),model.contracted());
            assertEquals(v.equals("2.3")?"2005-06-16":"2014-07-09",model.modelVersion());
            assertEquals("http://www.interlis.ch/",model.issuer());assertEquals("en",model.options().language().orElseThrow().value());
            assertTrue(model.header().nameRange().isEmpty());
            assertTrue(model.members().contains(canonical(out.analysis(),"SurfaceEdge").id()));
            for(var member:model.members())assertEquals(Optional.of(model.id()),graph.symbol(member).orElseThrow().owner());
            var own=(ModelSymbol)graph.symbol(graph.rootModels().getFirst()).orElseThrow();
            assertEquals(Optional.of(model.id()),own.imports().getFirst().targetModel());
            var source=out.analysis().syntaxSnapshots().values().iterator().next().source();
            int prefix=source.text().indexOf("INTERLIS.SurfaceEdge");
            assertEquals(model.id(),LanguageQueries.create().symbolAt(out.analysis(),source.range(prefix,prefix),CancellationToken.none()).known().orElseThrow().symbol());
        }
    }
}
