package guru.interlis.projection;
import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class P3PartialProjectionTest {
    @Test void missingImportsRemainVisibleAndOnlyAffectedRealNodesBecomePartial() {
        {
            var source=new SourceId("memory:/partial");String text="INTERLIS 2.4;MODEL F AT \"x\" VERSION \"1\"=IMPORTS Missing;TOPIC T=CLASS C=x:Missing.D;END C;CLASS Good=y:TEXT;END Good;END T;END F.";
            var input=new SourceText(source,new SourceRevision(1),text);
            var snapshot=CompilerEngines.create().analyze(new CompilationRequest(List.of(new ModelSelector.ByName("F",Optional.empty(),Optional.empty())),SourceSet.of(Map.of(source,input)),CompilerOptions.defaults()),CancellationToken.none());
            var refs=snapshot.semanticGraph().rootModels().stream().map(id->new SymbolRef(snapshot.snapshotId(),id)).toList();
            var doc=assertInstanceOf(DiagramProjectionOutcome.Produced.class,DiagramProjectors.create().project(new DiagramInput.FromAnalysis(snapshot),DiagramOptions.defaults(new DiagramSelection(refs)),CancellationToken.none())).document();
            assertEquals(ProjectionCompleteness.PARTIAL,doc.completeness());
            assertTrue(doc.nodes().stream().anyMatch(n->n.status()==DiagramNodeStatus.UNRESOLVED&&n.symbol().isEmpty()&&n.anchor().isEmpty()));
            assertEquals(DiagramNodeStatus.PARTIAL,doc.nodes().stream().filter(n->n.label().equals("C")).findFirst().orElseThrow().status());
            assertEquals(DiagramNodeStatus.RESOLVED,doc.nodes().stream().filter(n->n.label().equals("Good")).findFirst().orElseThrow().status());
            assertTrue(doc.edges().stream().anyMatch(e->e.kind()==DiagramEdge.EdgeKind.MODEL_IMPORT));
            assertTrue(doc.notices().stream().anyMatch(n->n.code()==ProjectionNoticeCode.UNRESOLVED_TARGET));
            assertTrue(new DiagramValidator().validate(doc).isEmpty());
        }
    }
}
