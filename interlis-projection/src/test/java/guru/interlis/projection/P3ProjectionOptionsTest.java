package guru.interlis.projection;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class P3ProjectionOptionsTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static CompiledSchema compile(String body){
        var source=new SourceText(new SourceId("memory:/options"),new SourceRevision(1),"INTERLIS 2.4;MODEL F AT \"x\" VERSION \"1\"="+body+"END F.");
        return assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(new CompilationRequest(List.of(new ModelSelector.ByName("F",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults()),TOKEN)).schema();
    }
    private static SymbolRef named(CompiledSchema schema,String name){return new SymbolRef(schema.snapshotId(),schema.semanticGraph().symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow().id());}
    private static DiagramDocument project(CompiledSchema schema,DiagramOptions options){return assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),options,TOKEN)).document();}
    @Test void aSelectedClassDoesNotExpandItsOwnersUnrelatedMembers(){
        var schema=compile("TOPIC T=CLASS A=n:TEXT;END A;CLASS B=n:TEXT;END B;END T;");
        var doc=project(schema,DiagramOptions.defaults(new DiagramSelection(List.of(named(schema,"A")))));
        assertEquals(List.of("A"),doc.nodes().stream().map(DiagramNode::label).toList());
        assertEquals(List.of("F","T"),doc.containers().stream().map(DiagramContainer::label).toList());
        assertTrue(new DiagramValidator().validate(doc).isEmpty());
    }
    private static DiagramOptions options(DiagramOptions base,DiagramScope scope,LabelPolicy policy,
            Optional<SymbolRef> translation,OptionalInt depth,ProjectionBudget budget){
        return new DiagramOptions(base.selection(),scope,base.visibleKinds(),base.inheritedMembers(),base.showTypes(),
                base.showRoleCardinalities(),base.showConstraints(),base.showImports(),policy,base.associationPresentation(),budget,translation,depth);
    }
    @Test void neighbourhoodHasExplicitBoundedDistanceAndRetainsBoundaryStubs(){
        var schema=compile("DOMAIN D=TEXT;TOPIC T=CLASS A=value:D;END A;CLASS B EXTENDS A=END B;CLASS C EXTENDS B=END C;CLASS Other=n:TEXT;END Other;END T;");
        var base=DiagramOptions.defaults(new DiagramSelection(List.of(named(schema,"A"))));
        for(int depth=0;depth<=2;depth++) {
            var doc=project(schema,options(base,DiagramScope.NEIGHBOURHOOD,LabelPolicy.CANONICAL,Optional.empty(),OptionalInt.of(depth),base.budget()));
            var labels=doc.nodes().stream().filter(n->n.kind()!=DiagramNodeKind.EXTERNAL_REFERENCE).map(DiagramNode::label).collect(java.util.stream.Collectors.toSet());
            assertEquals(depth==0?Set.of("A"):depth==1?Set.of("A","B","D"):Set.of("A","B","C","D"),labels);
            assertFalse(doc.nodes().stream().anyMatch(n->n.label().equals("Other")));
            assertTrue(new DiagramValidator().validate(doc).isEmpty());
        }
        var limited=project(schema,options(base,DiagramScope.NEIGHBOURHOOD,LabelPolicy.CANONICAL,Optional.empty(),OptionalInt.of(2),new ProjectionBudget(2,20,10)));
        assertEquals(ProjectionCompleteness.LIMITED,limited.completeness());assertTrue(limited.nodes().size()<=2);
        assertThrows(IllegalArgumentException.class,()->options(base,DiagramScope.NEIGHBOURHOOD,LabelPolicy.CANONICAL,Optional.empty(),OptionalInt.of(33),base.budget()));
        assertThrows(guru.interlis.execution.OperationCancelledException.class,()->DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),base,()->true));
    }
    @Test void translatedLabelsUseBoundCorrespondencesWithoutChangingCanonicalIdentityOrNavigation(){
        var source=new SourceText(new SourceId("memory:/translation"),new SourceRevision(1),
                "INTERLIS 2.4;MODEL English(en) AT \"x\" VERSION \"1\"=DOMAIN Identifier=TEXT*10;TOPIC Data=CLASS Road=name:Identifier;END Road;END Data;END English."
                +"MODEL French(fr) AT \"x\" VERSION \"1\" TRANSLATION OF English[\"1\"]=DOMAIN Identifiant=TEXT*10;TOPIC Donnees=CLASS Route=nom:Identifiant;END Route;END Donnees;END French.");
        var syntax=guru.interlis.syntax.SyntaxServices.create().parse(source,guru.interlis.syntax.ParseOptions.defaults(guru.interlis.syntax.ParseMode.EDITOR),TOKEN);
        var original=syntax.root().models().getFirst();var translated=syntax.root().models().getLast();
        var binding=new TranslationBinding(new ModelLocation(source.id(),source.revision(),translated.name(),Optional.of(translated.modelVersion()),translated.range().startOffset()),
                new ModelLocation(source.id(),source.revision(),original.name(),Optional.of(original.modelVersion()),original.range().startOffset()));
        var request=new CompilationRequest(List.of(new ModelSelector.ByName("French",Optional.empty(),Optional.empty())),
                SourceSet.of(Map.of(source.id(),source),List.of(),List.of(),ResolutionFingerprint.none(),List.of(binding)),CompilerOptions.defaults());
        var result=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,TOKEN));var schema=result.schema();
        var base=DiagramOptions.defaults(new DiagramSelection(List.of(named(schema,"English"))));
        var canonical=project(schema,base);var french=project(schema,options(base,base.scope(),LabelPolicy.TRANSLATED,Optional.of(named(schema,"French")),OptionalInt.empty(),base.budget()));
        assertEquals(canonical.nodes().stream().map(DiagramNode::id).toList(),french.nodes().stream().map(DiagramNode::id).toList());
        for(int i=0;i<canonical.nodes().size();i++) {
            var a=canonical.nodes().get(i);var b=french.nodes().get(i);assertEquals(a.canonicalName(),b.canonicalName());assertEquals(a.symbol(),b.symbol());assertEquals(a.anchor(),b.anchor());
        }
        var road=canonical.nodes().stream().filter(n->n.label().equals("Road")).findFirst().orElseThrow();var route=french.nodes().stream().filter(n->n.id().equals(road.id())).findFirst().orElseThrow();
        assertEquals("Route",route.label());var a=road.compartments().getFirst().members().getFirst();var b=route.compartments().getFirst().members().getFirst();
        assertEquals("name",a.label());assertEquals("nom",b.label());assertEquals(Optional.of("Identifiant"),b.typeLabel());
        assertEquals(a.id(),b.id());assertEquals(a.declaredSymbol(),b.declaredSymbol());assertEquals(a.anchor(),b.anchor());
        assertEquals(List.of("French","Donnees"),french.containers().stream().map(DiagramContainer::label).toList());
        assertTrue(new DiagramValidator().validate(french).isEmpty());assertEquals("Road",road.label());
        assertNotEquals(canonical.optionsFingerprint(),french.optionsFingerprint());
        var wrong=options(base,base.scope(),LabelPolicy.TRANSLATED,Optional.of(named(schema,"English")),OptionalInt.empty(),base.budget());
        assertInstanceOf(DiagramProjectionOutcome.Rejected.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),wrong,TOKEN));
        var stale=options(base,base.scope(),LabelPolicy.TRANSLATED,Optional.of(new SymbolRef(new SnapshotId("other"),named(schema,"French").symbol())),OptionalInt.empty(),base.budget());
        assertInstanceOf(DiagramProjectionOutcome.Rejected.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(schema),stale,TOKEN));
    }

    @Test void memberBudgetIsSharedAcrossCompartmentsAndTypeFilterAppliesToEveryMember() {
        var schema=compile("DOMAIN D=TEXT;TOPIC T=CLASS C=aa:D;bb:TEXT;MANDATORY CONSTRAINT R1:#true;MANDATORY CONSTRAINT R2:#true;END C;END T;");
        var base=DiagramOptions.defaults(new DiagramSelection(List.of(named(schema,"F"))));
        var complete=project(schema,base);assertEquals(ProjectionCompleteness.COMPLETE,complete.completeness());
        assertEquals(4,complete.nodes().stream().filter(n->n.label().equals("C")).findFirst().orElseThrow().compartments().stream().mapToInt(c->c.members().size()).sum());
        for(int limit:List.of(1,2,3,4)) {
            var bounded=project(schema,options(base,base.scope(),base.labelPolicy(),Optional.empty(),OptionalInt.empty(),new ProjectionBudget(100,100,limit)));
            assertTrue(bounded.nodes().stream().allMatch(n->n.compartments().stream().mapToInt(c->c.members().size()).sum()<=limit));
            assertEquals(limit<4?ProjectionCompleteness.LIMITED:ProjectionCompleteness.COMPLETE,bounded.completeness());
            assertEquals(limit<4,bounded.notices().stream().anyMatch(n->n.code()==ProjectionNoticeCode.PROJECTION_LIMIT_REACHED));
            assertTrue(new DiagramValidator().validate(bounded).isEmpty());
        }
        var hidden=project(schema,new DiagramOptions(base.selection(),base.scope(),base.visibleKinds(),base.inheritedMembers(),false,
                base.showRoleCardinalities(),base.showConstraints(),base.showImports(),base.labelPolicy(),base.associationPresentation(),base.budget()));
        assertTrue(hidden.nodes().stream().flatMap(n->n.compartments().stream()).flatMap(c->c.members().stream()).allMatch(m->m.typeLabel().isEmpty()));
        assertEquals(complete.nodes().stream().map(DiagramNode::id).toList(),hidden.nodes().stream().map(DiagramNode::id).toList());
        assertTrue(new DiagramValidator().validate(hidden).isEmpty());
    }
}
