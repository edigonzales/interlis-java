package guru.interlis.projection;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CompactAssociationProjectionTest {
    private static final String SOURCE="""
        INTERLIS 2.4; MODEL Compact AT "x" VERSION "1" = TOPIC T = OID AS INTERLIS.UUIDOID;
        CLASS A=END A; CLASS B=END B; CLASS C=END C;
        ASSOCIATION Self=parent--{0..1}A;children--{*}A;END Self;
        ASSOCIATION Owns=owner-<#>{1}A;items--{*}B;END Owns;
        ASSOCIATION Parallel=fromA--{1}A;toB--{*}B;END Parallel;
        ASSOCIATION Many=one--{1}A;two--{1}B;three--{1}C;note:TEXT;END Many;
        ASSOCIATION Data=dataA--{1}A;dataB--{1}B;note:TEXT;END Data;
        END T;END Compact.
        """;
    @Test void selfParallelAndCompositionRemainTypedWhileNaryAndOwnAttributesStayEntities() {
        var source=new SourceText(new SourceId("memory:/compact.ili"),new SourceRevision(1),SOURCE);
        var request=new CompilationRequest(List.of(new ModelSelector.BySource(source.id())),SourceSet.of(Map.of(source.id(),source)),CompilerOptions.defaults());
        var compiled=CompilerEngines.create().compile(request,CancellationToken.none());
        var outcome=assertInstanceOf(CompilationOutcome.Succeeded.class,compiled,()->compiled instanceof CompilationOutcome.Rejected rejected?rejected.analysis().diagnostics().toString():compiled.toString());
        var defaults=DiagramOptions.defaults(new DiagramSelection(outcome.schema().rootModels()));
        var options=new DiagramOptions(defaults.selection(),defaults.scope(),defaults.visibleKinds(),defaults.inheritedMembers(),true,true,true,true,defaults.labelPolicy(),AssociationPresentation.COMPACT_BINARY,defaults.budget());
        var document=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromSchema(outcome.schema()),options,CancellationToken.none())).document();
        assertEquals(ProjectionCompleteness.COMPLETE,document.completeness());assertTrue(new DiagramValidator().validate(document).isEmpty());
        var compact=document.edges().stream().filter(edge->edge.kind()==DiagramEdge.EdgeKind.ASSOCIATION).toList();assertEquals(3,compact.size());
        var self=compact.stream().filter(edge->edge.label().equals("Self")).findFirst().orElseThrow();assertEquals(self.source(),self.target());
        assertEquals("parent",self.sourceEnd().roleName().orElseThrow());assertEquals("children",self.targetEnd().roleName().orElseThrow());
        assertEquals(Cardinality.of(0,1),self.sourceEnd().cardinality().orElseThrow());assertEquals(Cardinality.unboundedFrom(0),self.targetEnd().cardinality().orElseThrow());
        assertEquals("Self",SOURCE.substring(self.anchor().orElseThrow().nameRange().startOffset(),self.anchor().orElseThrow().nameRange().endOffset()));
        var owns=compact.stream().filter(edge->edge.label().equals("Owns")).findFirst().orElseThrow();var parallel=compact.stream().filter(edge->edge.label().equals("Parallel")).findFirst().orElseThrow();
        assertEquals(owns.source(),parallel.source());assertEquals(owns.target(),parallel.target());assertNotEquals(owns.id(),parallel.id());
        assertEquals(RoleAggregation.COMPOSITION,owns.sourceEnd().aggregation());assertEquals(RoleAggregation.NONE,owns.targetEnd().aggregation());
        for(String name:List.of("Many","Data")){var node=document.nodes().stream().filter(value->value.label().equals(name)).findFirst().orElseThrow();assertTrue(node.compartments().stream().flatMap(c->c.members().stream()).anyMatch(member->member.label().equals("note")));}
        assertEquals(3,document.edges().stream().filter(edge->document.nodes().stream().anyMatch(node->node.label().equals("Many")&&node.id().equals(edge.source().elementId()))).count());
        for(String text:List.of(new MermaidRenderer().render(document),new PlantUmlRenderer().render(document))){assertTrue(text.contains("owner 1"));assertTrue(text.contains("items 0..*"));assertTrue(text.contains("*--"));assertTrue(text.contains("parent 0..1"));}
        assertTrue(document.nodes().stream().noneMatch(node->List.of("Self","Owns","Parallel").contains(node.label())));
    }
}
