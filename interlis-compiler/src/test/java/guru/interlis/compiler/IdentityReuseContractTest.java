package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** INC-010/011: continuity requires an unambiguous declaration and owner. */
class IdentityReuseContractTest {
    private static CompilationRequest request(String declarations,long revision) {
        var source=new SourceText(new SourceId("memory:/identity.ili"),new SourceRevision(revision),"INTERLIS 2.4;MODEL F AT \"https://example.org\" VERSION \"1\"="+declarations+"END F.");
        return CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static AnalysisSnapshot analyze(String declarations,long revision,AnalysisSnapshot previous) {
        return CompilerEngines.create().analyze(request(declarations,revision),previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),CancellationToken.none());
    }
    private static List<SymbolId> ids(AnalysisSnapshot snapshot,String name) {
        return snapshot.semanticGraph().qualifiedNames().getOrDefault(new QualifiedName(List.of(name.split("\\."))),List.of());
    }
    @Test void duplicateDeclarationsAndTheirChildrenNeverAcquireInventedContinuity() {
        String single="STRUCTURE C=a:TEXT;END C;";String duplicate=single+"STRUCTURE C=b:BOOLEAN;END C;";
        var first=analyze(single,1,null);var second=analyze(duplicate,2,first);
        var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(duplicate,2),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
        assertEquals(2,ids(second,"F.C").size());assertTrue(Collections.disjoint(ids(first,"F.C"),ids(second,"F.C")));
        assertTrue(Collections.disjoint(ids(first,"F.C.a"),ids(second,"F.C.a")),"A unique child under an ambiguous owner must not keep a false identity");
        var third=analyze(single,3,second);assertTrue(Collections.disjoint(ids(second,"F.C"),ids(third,"F.C")));
        assertTrue(Collections.disjoint(ids(second,"F.C.a"),ids(third,"F.C.a")));
        assertEquals(ids(first,"F"),ids(third,"F"));
        assertEquals(OriginalViewReuseTest.semantic(analyze(single,3,null).semanticGraph().symbols(),analyze(single,3,null).semanticGraph()),OriginalViewReuseTest.semantic(third.semanticGraph().symbols(),third.semanticGraph()));
    }
    @Test void allUniqueEnumElementsRetainTheirIdentitiesAcrossCommentsAndUnrelatedInsertions() {
        String declarations="DOMAIN E=(red,green,blue);STRUCTURE C=x:TEXT;END C;";
        var first=analyze(declarations,1,null);var second=analyze("!! unchanged declarations\nDOMAIN Before=TEXT;"+declarations,2,first);
        assertTrue(first.diagnostics().isEmpty());assertTrue(second.diagnostics().isEmpty());
        for(String name:List.of("F","F.E","F.E.red","F.E.green","F.E.blue","F.C","F.C.x"))assertEquals(ids(first,name),ids(second,name),name);
        assertEquals(3,ids(second,"F.E.red").size()+ids(second,"F.E.green").size()+ids(second,"F.E.blue").size());
    }
    @Test void deletionRecreationAndMovingToAnotherOwnerAllocateNewIdentities() {
        String declaration="STRUCTURE C=a:TEXT;END C;";
        var first=analyze(declaration,1,null);var deleted=analyze("",2,first);
        var recreated=analyze(declaration,3,deleted);
        assertTrue(Collections.disjoint(ids(first,"F.C"),ids(recreated,"F.C")));
        assertTrue(Collections.disjoint(ids(first,"F.C.a"),ids(recreated,"F.C.a")));
        assertEquals(ids(first,"F"),ids(recreated,"F"));
        var moved=analyze("TOPIC T="+declaration+"END T;",4,recreated);
        assertTrue(Collections.disjoint(ids(recreated,"F.C"),ids(moved,"F.T.C")));
        assertTrue(Collections.disjoint(ids(recreated,"F.C.a"),ids(moved,"F.T.C.a")));
        assertTrue(moved.diagnostics().isEmpty());
    }
}
