package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** AC2-03/04: bound formations, actual ALL OF participants and inherited context. */
class ViewFoundationContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final String COORD="DOMAIN Coord=COORD 0.00..100.00,0.00..100.00;";
    private static CompilationRequest input(String v,String body) {
        var s=new SourceText(new SourceId("memory:/view-foundation.ili"),new SourceRevision(9),
                "INTERLIS "+v+";\r\n!! 🦊\r\nCONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest in) {
        var out=CompilerEngines.create().compile(in,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static Symbol named(SemanticGraph g,String name) {
        return g.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static ViewSymbol view(SemanticGraph g,String name) {return assertInstanceOf(ViewSymbol.class,named(g,name));}
    private static List<String> names(SemanticGraph g,List<SymbolId> ids) {return ids.stream().map(id->g.symbol(id).orElseThrow().name().value()).toList();}
    @Test void allOfBindsEveryAncestorBeforeCopyingImportedAttributes() {
        for (String v : List.of("2.3", "2.4")) for (boolean reverse : List.of(false, true)) {
            var base = source(reverse ? "memory:/a-base" : "memory:/z-base", "INTERLIS " + v
                    + ";MODEL Base AT \"x\" VERSION \"1\"=CLASS Root(ABSTRACT)=stamp:TEXT;END Root;END Base.");
            var shared = source("memory:/m-shared", "INTERLIS " + v
                    + ";MODEL Shared AT \"x\" VERSION \"1\"=IMPORTS Base;"
                    + "CLASS Mid(ABSTRACT) EXTENDS Base.Root=flag:BOOLEAN;END Mid;"
                    + "TOPIC T=CLASS C EXTENDS Mid=number:0..10;END C;END T;END Shared.");
            var use = source(reverse ? "memory:/z-use" : "memory:/a-use", "INTERLIS " + v
                    + ";MODEL F AT \"x\" VERSION \"1\"=IMPORTS Shared;TOPIC T=DEPENDS ON Shared.T;"
                    + "VIEW V PROJECTION OF src~Shared.T.C;=ALL OF src;"
                    + "MANDATORY CONSTRAINT DEFINED(stamp);END V;END T;END F.");
            var in = request(List.of("F"), Map.of(base.id(),base,shared.id(),shared,use.id(),use),
                    List.of(binding(parse(shared),"Base",base,parse(base)),binding(parse(use),"Shared",shared,parse(shared))),CompilerOptions.defaults());
            var g = ok(in).schema().semanticGraph();
            assertEquals(List.of("stamp","flag","number"), names(g,view(g,"V").members()));
            for (var id : view(g,"V").members()) assertTrue(((AttributeSymbol)g.symbol(id).orElseThrow()).derivations().size()==1);
        }
    }
    @Test void inheritedAreaInspectionsCannotBypassTheAllOfProhibition() {
        for(String v:List.of("2.3","2.4"))for(boolean intermediate:List.of(false,true)) {
            String middle=intermediate?"VIEW W EXTENDS V=END W;":"";
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,COORD
                    +"TOPIC T=CLASS A=shape:AREA WITH(STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS>0.01;END A;"
                    +"VIEW V AREA INSPECTION OF edge~A->shape;=END V;"+middle
                    +"VIEW Z EXTENDS "+(intermediate?"W":"V")+"=ALL OF edge;END Z;END T;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertTrue(out.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")&&d.arguments().toString().contains("VIEW_ALL_OF")));
            assertTrue(((ViewSymbol.Inspection)view(out.analysis().semanticGraph(),"Z").formation().orElseThrow()).area());
        }
    }
    @Test void inheritedSurfaceInspectionsKeepTheirActualElementAndCopiedMemberTargets() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(input(v,COORD+"TOPIC T=CLASS A=shape:SURFACE WITH(STRAIGHTS) VERTEX Coord WITHOUT OVERLAPS>0.01;END A;"
                    +"VIEW V INSPECTION OF edge~A->shape;=END V;VIEW W EXTENDS V=ALL OF edge;END W;END T;")).schema().semanticGraph();
            var parent=view(g,"V");var child=view(g,"W");
            var formation=assertInstanceOf(ViewSymbol.Inspection.class,child.formation().orElseThrow());
            assertFalse(formation.area());assertEquals(parent.formation(),child.formation());assertEquals(Optional.of(parent.id()),child.baseView());
            assertEquals(List.of("Lines"),names(g,child.members()));
            var copy=assertInstanceOf(AttributeSymbol.class,g.symbol(child.members().getFirst()).orElseThrow());
            var original=assertInstanceOf(AttributeSymbol.class,g.symbol(((Origin.DerivedOrigin)copy.origin()).declaredAt()).orElseThrow());
            assertEquals(formation.element(),original.owner().orElseThrow());assertEquals(copy.declaredType(),original.declaredType());
            var path=assertInstanceOf(PathExpression.class,g.expression(copy.derivations().getFirst()).orElseThrow());
            assertEquals(formation.element(),assertInstanceOf(PathStep.ViewBaseStep.class,path.path().steps().getFirst()).target());
            assertEquals(original.id(),assertInstanceOf(PathStep.AttributeStep.class,path.path().steps().getLast()).target());
        }
    }
    @Test void allOfIn24CopiesAssociationRolesInOrderAndPreservesSingleParticipantTargets() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(input(v,"TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=code:TEXT;END A;CLASS B=name:TEXT;END B;"
                    +"ASSOCIATION Link=a--{1}A;b--{*}B;label:MANDATORY TEXT;END Link;"
                    +"VIEW V PROJECTION OF src~Link;=ALL OF src;END V;"
                    +"VIEW J JOIN OF lhs~A,rel~Link(OR NULL);=ALL OF rel;END J;END T;")).schema().semanticGraph();
            for(String name:List.of("V","J")) {
                var result=view(g,name);assertEquals(v.equals("2.4")?List.of("a","b","label"):List.of("label"),names(g,result.members()));
                var label=(AttributeSymbol)g.symbol(result.members().getLast()).orElseThrow();assertEquals(name.equals("V"),label.mandatory());
                assertEquals(result.members(),g.effectiveMembers(result.id()).stream().map(EffectiveMember::declaredSymbol).toList());
                if(v.equals("2.3"))continue;
                for(int i=0;i<2;i++) {
                    var copy=assertInstanceOf(AttributeSymbol.class,g.symbol(result.members().get(i)).orElseThrow());
                    var origin=assertInstanceOf(Origin.DerivedOrigin.class,copy.origin());
                    var role=assertInstanceOf(RoleSymbol.class,g.symbol(origin.declaredAt()).orElseThrow());
                    assertEquals(DerivationKind.ALIAS,origin.kind());assertEquals(copy.name(),role.name());
                    var type=assertInstanceOf(ObjectArgumentType.class,g.type(copy.declaredType()).orElseThrow());
                    assertFalse(type.objects(),"A participant of one link is one object even when the relationship cardinality is many");
                    assertEquals(role.targetRoots(),type.base().stream().toList());
                    var path=assertInstanceOf(PathExpression.class,g.expression(copy.derivations().getFirst()).orElseThrow());
                    assertEquals(named(g,"Link").id(),assertInstanceOf(PathStep.ViewBaseStep.class,path.path().steps().getFirst()).target());
                    assertEquals(role.id(),assertInstanceOf(PathStep.RoleStep.class,path.path().steps().getLast()).target());
                }
            }
        }
    }
    @Test void projectionSelectionAndThreeLevelInheritanceKeepOriginalTypesAndMemberPositions() {
        for(String v:List.of("2.3","2.4")) {
            var in=input(v,"TOPIC T=CLASS A=number:0..10;label:TEXT*20;END A;"
                    +"VIEW V PROJECTION OF src~A;WHERE src->number>0;=ALL OF src;END V;"
                    +"VIEW W EXTENDS V=label(EXTENDED):MANDATORY;END W;VIEW Z EXTENDS W=END Z;END T;");
            var out=ok(in);var g=out.schema().semanticGraph();var base=view(g,"V");var middle=view(g,"W");var end=view(g,"Z");
            var projection=(ViewSymbol.Projection)base.formation().orElseThrow();
            var basis=(ViewSymbol.Base)g.symbol(projection.base()).orElseThrow();assertEquals(named(g,"A").id(),basis.target());
            assertEquals(List.of("number","label"),names(g,end.members()));assertEquals(base.selections(),end.selections());
            var inherited=g.effectiveMembers(end.id());
            assertEquals(List.of(base.id(),middle.id(),end.id()),inherited.getFirst().inheritancePath());
            assertEquals(List.of(middle.id(),end.id()),inherited.getLast().inheritancePath());
            assertEquals(List.of(0,1),inherited.stream().map(EffectiveMember::position).toList());
            var original=(AttributeSymbol)g.symbol(base.members().getLast()).orElseThrow();var copy=(AttributeSymbol)g.symbol(end.members().getLast()).orElseThrow();
            assertTrue(copy.mandatory());assertEquals(original.declaredType(),copy.declaredType());assertEquals(Optional.of(original.id()),copy.overrides());
            var source=in.sources().sources().values().iterator().next();int at=source.text().indexOf("WHERE src")+6;
            var target=LanguageQueries.create().definition(out.analysis(),source.range(at,at),TOKEN).known().orElseThrow();
            assertEquals(basis.id(),target.symbol().symbol());assertEquals("src",source.slice(target.nameRange()));
            assertThrows(UnsupportedOperationException.class,()->end.members().clear());
        }
    }
    @Test void nullableJoinAndUnionKeepDifferentOrderedDerivationTargets() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(input(v,"TOPIC T=CLASS A=number:MANDATORY 0..10;label:TEXT*12;END A;CLASS B=other:MANDATORY TEXT*20;END B;"
                    +"VIEW J JOIN OF a~A,b~B(OR NULL);=ALL OF a;ALL OF b;END J;"
                    +"VIEW U UNION OF a~A,b~B;=value:MANDATORY TEXT*30:=a->label,b->other;END U;END T;")).schema().semanticGraph();
            var join=view(g,"J");assertEquals(join.bases(),((ViewSymbol.Join)join.formation().orElseThrow()).bases());
            assertEquals(List.of("number","label","other"),names(g,join.members()));
            assertTrue(((AttributeSymbol)g.symbol(join.members().getFirst()).orElseThrow()).mandatory());
            assertFalse(((AttributeSymbol)g.symbol(join.members().getLast()).orElseThrow()).mandatory());
            assertTrue(((ViewSymbol.Base)g.symbol(join.bases().getLast()).orElseThrow()).orNull());
            var union=view(g,"U");assertEquals(union.bases(),((ViewSymbol.Union)union.formation().orElseThrow()).bases());
            var value=(AttributeSymbol)g.symbol(union.members().getFirst()).orElseThrow();assertEquals(2,value.derivations().size());
            var targets=new ArrayList<SymbolId>();var bases=new ArrayList<SymbolId>();
            for(var id:value.derivations()) {
                var path=(PathExpression)g.expression(id).orElseThrow();
                bases.add(((PathStep.ViewBaseStep)path.path().steps().getFirst()).base());
                targets.add(((PathStep.AttributeStep)path.path().steps().getLast()).target());
            }
            assertEquals(union.bases(),bases);assertEquals(List.of("label","other"),names(g,targets));
            assertEquals(List.of(named(g,"A").id(),named(g,"B").id()),targets.stream().map(id->g.symbol(id).orElseThrow().owner().orElseThrow()).toList());
        }
    }
    @Test void aggregationKeepsEqualPathsAndItsNonTransferBagThroughAnExtension() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(input(v,"TOPIC T=CLASS A=number:0..10;label:TEXT;END A;"
                    +"VIEW V AGGREGATION OF src~A EQUAL(src->number,src->label);=count:=INTERLIS.elementCount(AGGREGATES);END V;"
                    +"VIEW W EXTENDS V=again:=INTERLIS.elementCount(AGGREGATES);END W;END T;")).schema().semanticGraph();
            var base=view(g,"V");var child=view(g,"W");var formation=(ViewSymbol.Aggregation)child.formation().orElseThrow();
            assertEquals(base.formation(),child.formation());assertEquals(2,formation.equal().size());
            assertEquals(List.of("count","again"),names(g,child.members()));
            var aggregates=assertInstanceOf(AttributeSymbol.class,named(g,"AGGREGATES"));assertEquals(base.id(),aggregates.owner().orElseThrow());
            assertFalse(child.members().contains(aggregates.id()));assertTrue(aggregates.transientAttribute());
            var bag=assertInstanceOf(CollectionType.class,g.type(aggregates.declaredType()).orElseThrow());assertEquals(CollectionKind.BAG,bag.collectionKind());
            assertEquals(named(g,"A").id(),((StructureValueType)g.type(bag.elementType()).orElseThrow()).target().orElseThrow());
            var again=(AttributeSymbol)g.symbol(child.members().getLast()).orElseThrow();
            var call=(FunctionCallExpression)g.expression(again.derivations().getFirst()).orElseThrow();
            assertEquals(aggregates.id(),((PathStep.AttributeStep)((PathExpression)g.expression(call.children().getFirst()).orElseThrow()).path().steps().getFirst()).target());
        }
    }
    @Test void nestedStructureInspectionKeepsTheImmediateParentAndElementIdentities() {
        for(String v:List.of("2.3","2.4")) {
            var g=ok(input(v,"STRUCTURE Item=code:TEXT;END Item;STRUCTURE Group=title:TEXT;items:BAG OF Item;END Group;"
                    +"TOPIC T=CLASS A=title:BOOLEAN;groups:BAG OF Group;END A;"
                    +"VIEW V INSPECTION OF item~A->groups->items;=ALL OF item;parentTitle:=PARENT->title;END V;END T;")).schema().semanticGraph();
            var inspection=view(g,"V");var formation=(ViewSymbol.Inspection)inspection.formation().orElseThrow();
            assertEquals(named(g,"Item").id(),formation.element());assertEquals(named(g,"Group").id(),formation.parent());
            assertEquals(List.of("code","parentTitle"),names(g,inspection.members()));
            var title=(AttributeSymbol)g.symbol(inspection.members().getLast()).orElseThrow();
            var path=(PathExpression)g.expression(title.derivations().getFirst()).orElseThrow();
            assertEquals(formation.parent(),((PathStep.InspectionStep)path.path().steps().getFirst()).target());
            var target=g.symbol(((PathStep.AttributeStep)path.path().steps().getLast()).target()).orElseThrow();
            assertEquals(formation.parent(),target.owner().orElseThrow());assertInstanceOf(TextType.class,g.type(title.declaredType()).orElseThrow());
        }
    }
    @Test void importedAllOfRolesFollowSourceRevisionsWithoutMutatingPublishedViews() {
        for(boolean reverse:List.of(false,true)) {
            var id=new SourceId(reverse?"memory:/z-shared.ili":"memory:/a-shared.ili");
            var use=source(reverse?"memory:/a-use.ili":"memory:/z-use.ili","INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"=IMPORTS Shared;"
                    +"TOPIC T=DEPENDS ON Shared.T;CLASS B=END B;VIEW V PROJECTION OF src~Shared.T.Link;=ALL OF src;END V;END T;END F.");
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;SemanticGraph first=null;SymbolId firstTarget=null;long revision=0;
            for(String target:List.of("B","C","B")) {
                var shared=new SourceText(id,new SourceRevision(++revision),"INTERLIS 2.4;MODEL Shared AT \"https://example.org\" VERSION \"1\"="
                        +"TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;CLASS C=END C;"
                        +"ASSOCIATION Link=a--{1}A;b--{*}"+target+";END Link;END T;END Shared.");
                var in=request(List.of("F"),Map.of(shared.id(),shared,use.id(),use),List.of(binding(parse(use),"Shared",shared,parse(shared))),CompilerOptions.defaults());
                var fresh=CompilerEngines.create().analyze(in,TOKEN);
                var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertTrue(reused.diagnostics().isEmpty(),reused.diagnostics().toString());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));
                var g=reused.semanticGraph();var result=view(g,"V");assertEquals(List.of("a","b"),names(g,result.members()));
                var b=(AttributeSymbol)g.symbol(result.members().getLast()).orElseThrow();
                var type=(ObjectArgumentType)g.type(b.declaredType()).orElseThrow();var actual=g.symbol(type.base().orElseThrow()).orElseThrow();
                assertEquals(target,actual.name().value());
                var topic=g.symbol(actual.owner().orElseThrow()).orElseThrow();assertEquals("Shared",g.symbol(topic.owner().orElseThrow()).orElseThrow().name().value());
                if(first==null){first=g;firstTarget=actual.id();}
                assertEquals("B",first.symbol(firstTarget).orElseThrow().name().value());
                previous=reused;
            }
        }
    }
    @Test void copiedRoleValuesRemainNavigableAcrossDerivedViews() {
        var in=input("2.4","TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=name:TEXT;END B;"
                +"ASSOCIATION Link=a--{1}A;b--{*}B;END Link;VIEW V PROJECTION OF rel~Link;=ALL OF rel;END V;"
                +"VIEW W PROJECTION OF src~V;=name:=src->b->name;END W;END T;");
        var g=ok(in).schema().semanticGraph();var w=view(g,"W");
        var attribute=(AttributeSymbol)g.symbol(w.members().getFirst()).orElseThrow();
        assertInstanceOf(TextType.class,g.type(attribute.declaredType()).orElseThrow());
        var path=(PathExpression)g.expression(attribute.derivations().getFirst()).orElseThrow();
        var copied=(PathStep.AttributeStep)path.path().steps().get(1);
        assertEquals(view(g,"V").members().get(1),copied.target());
        var target=g.symbol(((PathStep.AttributeStep)path.path().steps().getLast()).target()).orElseThrow();
        assertEquals(named(g,"B").id(),target.owner().orElseThrow());
    }

}
