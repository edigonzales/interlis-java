package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 2.3 §2.7.2/4 and 2.4 §3.7.2/4, including inherited role semantics. */
class AssociationStrengthContractTest {
    private static CompilationRequest request(String version,String definitions) {
        var source=CompilerTestSupport.source("memory:/strength.ili","INTERLIS "+version+";MODEL F AT \"https://example.org\" VERSION \"1\"=TOPIC T=OID AS INTERLIS.UUIDOID;CLASS A=END A;CLASS B=END B;CLASS C=END C;"+definitions+"END T;END F.");
        return CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(String version,String definitions) {
        var outcome=CompilerEngines.create().compile(request(version,definitions),CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class,outcome,outcome.toString());
    }
    private static void bad(String version,String definitions,String rule) {
        var outcome=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(request(version,definitions),CancellationToken.none()));
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),outcome.rejection().reasons());
        assertTrue(outcome.analysis().diagnostics().stream().anyMatch(d->d.code().value().equals("ILI-SEM-0010")&&d.arguments().toString().contains(rule)),outcome.analysis().diagnostics().toString());
    }
    @Test void aggregationRequiresExactlyTwoEffectiveRoles() {
        for(String version:List.of("2.3","2.4")) {
            bad(version,"ASSOCIATION Link=a-<>{1}A;b--{*}B;c--{*}C;END Link;","AGGREGATION_ARITY");
            var graph=ok(version,"ASSOCIATION Link=a-<>{1}A;b--{*}B;END Link;").schema().semanticGraph();
            var whole=graph.symbols().stream().filter(RoleSymbol.class::isInstance).map(RoleSymbol.class::cast).filter(r->r.name().value().equals("a")).findFirst().orElseThrow();
            assertEquals(RoleAggregation.AGGREGATION,whole.aggregation());
            assertEquals("A",graph.symbol(whole.targetRoots().getFirst()).orElseThrow().name().value());
        }
    }
    @Test void orderedRolesKeepTheirEffectiveOrderAndCannotBecomeUnordered() {
        String base="ASSOCIATION Base=a--{1}A;b(ORDERED)--{*}B;END Base;";
        for(String version:List.of("2.3","2.4")) {
            bad(version,base+"ASSOCIATION Sub EXTENDS Base=a(EXTENDED)--{1}A;b(EXTENDED)--{*}B;END Sub;","ROLE_ORDER_EXTENSION");
            var out=ok(version,base+"ASSOCIATION Sub EXTENDS Base=a(EXTENDED)--{1}A;b(EXTENDED,ORDERED)--{*}B;END Sub;");var graph=out.schema().semanticGraph();
            var sub=graph.symbols().stream().filter(AssociationSymbol.class::isInstance).map(AssociationSymbol.class::cast).filter(a->a.name().value().equals("Sub")).findFirst().orElseThrow();
            var members=out.schema().queries().effectiveMembers(new SymbolRef(out.schema().snapshotId(),sub.id())).known().orElseThrow();
            assertEquals(List.of("a","b"),members.stream().map(m->graph.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            assertFalse(((RoleSymbol)graph.symbol(members.getFirst().declaredSymbol()).orElseThrow()).ordered());
            assertTrue(((RoleSymbol)graph.symbol(members.get(1).declaredSymbol()).orElseThrow()).ordered());
        }
    }
    @Test void associationStrengthMayIncreaseButCannotWeakenOrHaveTwoWholeRoles() {
        for(String version:List.of("2.3","2.4")) {
            String base="ASSOCIATION Base=a-<>{1}A;b--{*}B;END Base;";
            var graph=ok(version,base+"ASSOCIATION Sub EXTENDS Base=a(EXTENDED)-<#>{1}A;b(EXTENDED)--{*}B;END Sub;").schema().semanticGraph();
            assertTrue(graph.symbols().stream().filter(RoleSymbol.class::isInstance).map(RoleSymbol.class::cast).anyMatch(r->r.aggregation()==RoleAggregation.COMPOSITION));
            bad(version,base+"ASSOCIATION Sub EXTENDS Base=a(EXTENDED)--{1}A;b(EXTENDED)--{*}B;END Sub;","ROLE_EXTENSION");
            bad(version,"ASSOCIATION Link=a-<>{1}A;b-<>{1}B;END Link;","MULTIPLE_AGGREGATION");
            bad(version,"ASSOCIATION Link=a-<#>{*}A;b--{*}B;END Link;","COMPOSITION_CARDINALITY");
        }
    }
    @Test void version23ModelLevelTargetsRetainTheApprovedCompatibilityProfile() {
        String prefix="INTERLIS 2.3;MODEL F AT \"https://example.org\" VERSION \"1\"=CLASS Outside=END Outside;TOPIC T=CLASS Local=END Local;";
        String relation="ASSOCIATION Link=a--{1}Outside;b--{*}Local;END Link;";
        var source=CompilerTestSupport.source("memory:/model-class.ili",prefix+relation+"END T;END F.");
        var request=CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
        var compatible=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,CancellationToken.none()));
        var compatibleGraph=compatible.schema().semanticGraph();
        var outsideRole=compatibleGraph.symbols().stream().filter(RoleSymbol.class::isInstance).map(RoleSymbol.class::cast).filter(r->r.name().value().equals("a")).findFirst().orElseThrow();
        var outside=compatibleGraph.symbol(outsideRole.targetRoots().getFirst()).orElseThrow();assertEquals("Outside",outside.name().value());
        assertInstanceOf(ModelSymbol.class,compatibleGraph.symbol(outside.owner().orElseThrow()).orElseThrow());
        var repaired=CompilerTestSupport.source(source.id().value(),(prefix+relation+"END T;END F.").replace("TOPIC T=CLASS Local","TOPIC T=CLASS Inside EXTENDS Outside=END Inside;CLASS Local").replace("{1}Outside","{1}Inside"));
        var success=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(repaired.id(),repaired),List.of(),CompilerOptions.defaults()),CancellationToken.none()));
        var graph=success.schema().semanticGraph();var role=graph.symbols().stream().filter(RoleSymbol.class::isInstance).map(RoleSymbol.class::cast).filter(r->r.name().value().equals("a")).findFirst().orElseThrow();
        assertEquals("Inside",graph.symbol(role.targetRoots().getFirst()).orElseThrow().name().value());
    }
    @Test void pinnedOriginalModelClassAssociationRetainsItsCompletePublicTargetAndStrength() throws Exception {
        var bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("testdata.dir"),"g2/a3/association-model-class23.ili"));
        assertEquals("41eadc08d51b09a7f23860267f8b8bf8e7a7b3a2805a68e5fa3f9380f717fa29",guru.interlis.source.Hex.encode(guru.interlis.source.Sha256.digest(bytes)));
        var source=CompilerTestSupport.source("memory:/original-model-class.ili",new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        var request=CompilerTestSupport.request(List.of("Basis"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
        var out=assertInstanceOf(CompilationOutcome.Succeeded.class,CompilerEngines.create().compile(request,CancellationToken.none()));var graph=out.schema().semanticGraph();
        var parent=(RoleSymbol)out.schema().queries().symbol(out.schema().queries().findQualifiedName(QualifiedName.of("Basis","Kataloge","Hierarchie","Parent")).getFirst()).known().orElseThrow();
        var target=out.schema().queries().findQualifiedName(QualifiedName.of("Basis","KatalogElem")).getFirst();assertEquals(List.of(target.symbol()),parent.targetRoots());
        assertEquals(RoleAggregation.AGGREGATION,parent.aggregation());assertTrue(graph.symbol(target.symbol()).orElseThrow().header().has(Modifier.ABSTRACT));
        assertInstanceOf(ModelSymbol.class,graph.symbol(graph.symbol(target.symbol()).orElseThrow().owner().orElseThrow()).orElseThrow());
    }
}
