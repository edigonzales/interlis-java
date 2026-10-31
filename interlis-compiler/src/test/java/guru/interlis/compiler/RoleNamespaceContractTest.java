package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Component names belong to their actual declaration/navigation namespace. */
class RoleNamespaceContractTest {
    private static CompilationOutcome compile(String version,String classes,String associations) {
        var source=CompilerTestSupport.source("memory:/role-namespaces","INTERLIS "+version+";MODEL F AT \"x\" VERSION \"1\"=TOPIC T=OID AS INTERLIS.UUIDOID;"+classes+associations+"END T;END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }
    @Test void unrelatedClassAndOwnTargetAttributesDoNotPolluteTheOppositeNavigationNamespace() {
        for(var version:List.of("2.3","2.4"))for(var classes:List.of("CLASS A=a:TEXT;END A;CLASS B=END B;", "CLASS A=END A;CLASS B=END B;CLASS U=b:TEXT;END U;")) {
            var result=compile(version,classes,"ASSOCIATION Link=a--A;b--B;END Link;");
            assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->result instanceof CompilationOutcome.Rejected rejected?rejected.analysis().diagnostics().toString():result.toString());
        }
    }
    @Test void actualOppositeRoleAndAssociationLocalAttributeCollisionsStayRejected() {
        for(var version:List.of("2.3","2.4"))for(boolean local:List.of(false,true)) {
            var result=compile(version,local?"CLASS A=END A;CLASS B=END B;":"CLASS A=b:TEXT;END A;CLASS B=END B;",
                    local?"ASSOCIATION Link=a--A;b--B;ATTRIBUTE a:TEXT;END Link;":"ASSOCIATION Link=a--A;b--B;END Link;");
            assertInstanceOf(CompilationOutcome.Rejected.class,result);
        }
    }
    @Test void inheritanceKeepsAvailableRoleNamesWithoutRetroactivelyChangingEarlierSubclasses() {
        for(var version:List.of("2.3","2.4")) {
            assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,"CLASS Base=b:TEXT;END Base;CLASS A EXTENDS Base=END A;CLASS B=END B;","ASSOCIATION Link=a--Base;b--B;END Link;"));
            String classes="CLASS Base=END Base;CLASS A EXTENDS Base=b:TEXT;END A;CLASS B=END B;";
            assertInstanceOf(CompilationOutcome.Succeeded.class,compile(version,classes,"ASSOCIATION Link=a--Base;b--B;END Link;"));
            assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,"CLASS Base=END Base;CLASS B=END B;",
                    "ASSOCIATION Link=a--Base;b--B;END Link;CLASS A EXTENDS Base=b:TEXT;END A;"));
        }
    }
    @Test void duplicateIncomingRoleNamesAreRejectedWithoutLeakingToOtherTargets() {
        for(var version:List.of("2.3","2.4")) {
            String classes="CLASS A=END A;CLASS B=END B;CLASS C=END C;CLASS D=END D;";
            String associations="ASSOCIATION First=a--A;b--B;END First;ASSOCIATION Second=c--C;b--D;END Second;";
            assertInstanceOf(CompilationOutcome.Succeeded.class,compile(version,classes,associations));
            var collision=assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,classes,associations.replace("c--C","c--A")));
            assertTrue(collision.analysis().diagnostics().stream().anyMatch(d->d.arguments().toString().contains("ROLE_NAMESPACE_CONFLICT")));
        }
    }
}
