package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import guru.interlis.compiler.query.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoOidReferenceContractTest {
    private static String model(String version,String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationOutcome compile(String version,String body) {
        var source=CompilerTestSupport.source("memory:/no-oid.ili",model(version,body));
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults()),CancellationToken.none());
    }
    private static CompilationOutcome.Succeeded ok(String version,String body) {
        var result=compile(version,body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,result,() -> result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics().toString() : result.toString());
    }
    private static CompilationOutcome.Rejected bad(String version,String body,String reason) {
        var r=assertInstanceOf(CompilationOutcome.Rejected.class,compile(version,body));
        assertTrue(r.analysis().diagnostics().toString().contains(reason),() -> r.analysis().diagnostics().toString());
        assertFalse(r.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT)); return r;
    }
    private static Symbol named(SemanticGraph graph,String name) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static String reference(String oid,String target) {
        return "TOPIC T = CLASS A = " + oid + " END A; STRUCTURE S = value: REFERENCE TO " + target + "; END S; END T;";
    }
    private static String roles(String oid,String target) {
        return "TOPIC T = CLASS A = " + oid + " END A; CLASS Owner = OID AS INTERLIS.UUIDOID; END Owner; "
                + "ASSOCIATION Link = target -- " + target + "; owner -- Owner; END Link; END T;";
    }

    @Test void originalCoordSysBindsAssociationRolesWithoutObjectOids() throws Exception {
        var path = Path.of("../testdata/conformance/no-oid/CoordSys.ili");
        byte[] bytes = Files.readAllBytes(path);
        assertEquals("b6ee5e9c9a74f9d75d5223cdf3f8313f1ae850d00b356ca682b3e8868f4237b2",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var source = CompilerTestSupport.source("memory:/CoordSys.ili", new String(bytes, StandardCharsets.UTF_8));
        var request = CompilerTestSupport.request(List.of("CoordSys"), Map.of(source.id(), source), List.of(),
                CompilerOptions.defaults());
        var result = CompilerEngines.create().compile(request, CancellationToken.none());
        var graph = assertInstanceOf(CompilationOutcome.Succeeded.class, result, result::toString)
                .schema().semanticGraph();
        assertRoleTargets(graph, "HeightEllips", List.of("GeoHeight", "Ellipsoid"));
    }

    static void assertRoleTargets(SemanticGraph graph, String name, List<String> targets) {
        var association = (AssociationSymbol) named(graph, name);
        var classes = association.roles().stream()
                .map(id -> (RoleSymbol) graph.symbol(id).orElseThrow())
                .map(role -> (ClassSymbol) graph.symbol(role.targetRoots().getFirst()).orElseThrow())
                .toList();
        assertEquals(targets, classes.stream().map(target -> target.name().value()).toList());
        assertTrue(classes.stream().allMatch(target -> target.header().oidRules().isEmpty()));
    }

    @Test void rolesAndAttributesAcceptClassesWithoutStableObjectOids() {
        for (String version : List.of("2.3", "2.4")) {
            for (String oid : List.of("", "NO OID;", "OID AS INTERLIS.UUIDOID;")) {
                ok(version, reference(oid, "A"));
                ok(version, roles(oid, "A"));
            }
        }
        for (String oid : List.of("", "NO OID;", "OID AS INTERLIS.NOOID;")) {
            var graph = ok("2.4", reference(oid, "A")).schema().semanticGraph();
            var rules = named(graph, "A").header().oidRules();
            if (oid.isEmpty()) {
                assertTrue(rules.isEmpty());
            } else {
                assertEquals(1, rules.size());
                var domain = rules.getFirst().domain();
                if (oid.equals("NO OID;")) assertTrue(domain.isEmpty());
                else assertEquals("INTERLIS.NOOID", graph.symbol(domain.orElseThrow()).orElseThrow().name().value());
            }
            ok("2.4", roles(oid, "A"));
        }
    }

    private static final String SETS="TOPIC T (ABSTRACT) = CLASS Root = NO OID; END Root; "
            + "CLASS Stable (ABSTRACT) EXTENDS Root = OID AS INTERLIS.ANYOID; END Stable; "
            + "CLASS Concrete EXTENDS Stable = OID AS INTERLIS.UUIDOID; flag: BOOLEAN; END Concrete; "
            + "CLASS Other = NO OID; END Other; CLASS Owner = OID AS INTERLIS.UUIDOID; END Owner; ";
    @Test void restrictionsSelectAllowedRootsAndEveryRoleAlternativeIsChecked() {
        ok("2.4",SETS+"ASSOCIATION Link = a -- Root RESTRICTION (Stable); b -- Owner; END Link; END T;");
        ok("2.4",SETS+"ASSOCIATION Link = a -- Stable OR Other; b -- Owner; END Link; END T;");
        ok("2.4",SETS+"ASSOCIATION Link = a -- ANYCLASS RESTRICTION (Stable; Other); b -- Owner; END Link; END T;");
        ok("2.4",SETS+"STRUCTURE S = r: REFERENCE TO ANYCLASS RESTRICTION (Concrete); END S; END T;");
        ok("2.4",SETS+"STRUCTURE S = r: REFERENCE TO ANYCLASS RESTRICTION (Concrete; Other); END S; END T;");
        bad("2.4",SETS+"STRUCTURE S = r: REFERENCE TO Concrete RESTRICTION (Owner); END S; END T;","REFERENCE_TARGET_RESTRICTION");
    }

    @Test void referenceRestrictionsRemainBoundNavigableAndUsableInPaths() {
        String body=SETS+"STRUCTURE S = r: REFERENCE TO ANYCLASS RESTRICTION (Concrete); MANDATORY CONSTRAINT r->flag; END S; END T;";
        var result=ok("2.4",body);var graph=result.schema().semanticGraph();
        var attribute=(AttributeSymbol)named(graph,"r");
        var type=(ReferenceType)graph.type(attribute.declaredType()).orElseThrow();
        assertTrue(type.target().isEmpty());assertEquals(List.of(named(graph,"Concrete").id()),type.restrictions());
        var path=graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast)
                .filter(p -> p.path().steps().size()==2).findFirst().orElseThrow();
        assertTrue(path.path().steps().stream().anyMatch(step -> step.toString().contains(named(graph,"flag").id().value())));
        var source=CompilerTestSupport.source("memory:/no-oid.ili",model("2.4",body));
        int start=source.text().indexOf("RESTRICTION (Concrete)")+13;
        var handle=LanguageQueries.create().symbolAt(result.analysis(),source.range(start,start),CancellationToken.none()).known().orElseThrow();
        assertEquals(named(graph,"Concrete").id(),handle.symbol());
    }

    @Test void oidInheritanceAndExtendedTopicTargetsAreUsed() {
        String body="TOPIC T (ABSTRACT) = CLASS A (ABSTRACT) = OID AS INTERLIS.ANYOID; END A; "
                + "CLASS Owner = OID AS INTERLIS.UUIDOID; END Owner; ASSOCIATION Link = a -- A; owner -- Owner; END Link; END T; "
                + "TOPIC U EXTENDS T = CLASS A (EXTENDED) = OID AS INTERLIS.UUIDOID; END A; "
                + "STRUCTURE S = value: REFERENCE TO A; END S; END U;";
        ok("2.4",body);
        bad("2.4", body.replace("OID AS INTERLIS.UUIDOID; END A;", "NO OID; END A;"), "OID_OVERRIDE");
        ok("2.3",reference("NO OID;","A"));
    }

    @Test void associationTargetsRequireOwnIdentityInBothVersions() {
        String body="TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; CLASS B = END B; "
                + "ASSOCIATION Link = NO OID; a -- A; b -- B; END Link; "
                + "STRUCTURE S = value: REFERENCE TO Link; END S; END T;";
        for(String version:List.of("2.3","2.4")) {
            bad(version,body,"REFERENCE_ASSOCIATION_OID");
            ok(version,body.replace("NO OID;","OID AS INTERLIS.UUIDOID;"));
            String modifier=body.replace("TOPIC T = OID AS INTERLIS.UUIDOID;","TOPIC T =")
                    .replace("CLASS A =","CLASS A = OID AS INTERLIS.UUIDOID;")
                    .replace("CLASS B =","CLASS B = OID AS INTERLIS.UUIDOID;")
                    .replace("ASSOCIATION Link = NO OID;","ASSOCIATION Link (OID) =");
            ok(version,modifier);
        }
    }

    @Test void collectionsAndInheritedReferenceAttributesAcceptNoOid() {
        ok("2.4",reference("NO OID;","A").replace("value: REFERENCE", "value: BAG OF REFERENCE"));
        String body="TOPIC T = CLASS A = OID AS INTERLIS.UUIDOID; END A; "
                + "STRUCTURE S = value: REFERENCE TO A; END S; STRUCTURE Child EXTENDS S = END Child; END T;";
        ok("2.4",body);
        ok("2.4",body.replace("OID AS INTERLIS.UUIDOID;","NO OID;"));
    }

    @Test void externalAndAnyClassAreIndependentOfStableIdentity() {
        for (String version : List.of("2.3", "2.4")) {
            ok(version, reference("NO OID;", "ANYCLASS"));
            ok(version, roles("NO OID;", "ANYCLASS"));
            String external = reference("NO OID;", "(EXTERNAL) A");
            String externalRole = roles("NO OID;", "A").replace("target --", "target (EXTERNAL) --");
            for (String body : List.of(external, externalRole)) {
                ok(version, body);
            }
            String association = "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; CLASS B = END B; "
                    + "ASSOCIATION Link = NO OID; a -- A; b -- B; END Link; "
                    + "ASSOCIATION Meta = value -- Link; other -- A; END Meta; END T;";
            bad(version, association, "REFERENCE_ASSOCIATION_OID");
            ok(version, association.replace("NO OID;", "OID AS INTERLIS.UUIDOID;"));
        }
    }

    @Test void crossTopicRolesAndAttributesWithoutOidStillNeedScopeDeclarations() {
        String topics = "TOPIC Base = CLASS A = NO OID; END A; END Base; TOPIC T = DEPENDS ON Base; ";
        String attribute = topics + "STRUCTURE S = r: REFERENCE TO (EXTERNAL) Base.A; END S; END T;";
        String association = topics + "CLASS B = END B; ASSOCIATION Link = "
                + "target (EXTERNAL) -- Base.A; owner -- B; END Link; END T;";
        ok("2.4", attribute);
        ok("2.4", association);
        bad("2.4", attribute.replace("(EXTERNAL) ", ""), "REFERENCE_EXTERNAL");
        bad("2.4", association.replace("(EXTERNAL) ", ""), "ROLE_EXTERNAL");
        for (String body : List.of(attribute, association)) {
            bad("2.4", body.replace("DEPENDS ON Base; ", ""), "REFERENCE_DEPENDS_ON");
        }
    }

    @Test void crossTopicReferencesNeedExternalIncludingRestrictionsAndCollections() {
        for (String version : List.of("2.3", "2.4")) {
            String body = "TOPIC Base = OID AS INTERLIS.UUIDOID; CLASS A = END A; END Base; "
                    + "TOPIC T = DEPENDS ON Base; STRUCTURE S = r: REFERENCE TO (EXTERNAL) Base.A; END S; END T;";
            ok(version, body);
            bad(version, body.replace("(EXTERNAL) ", ""), "REFERENCE_EXTERNAL");
            ok(version, body.replace("Base.A", "ANYCLASS RESTRICTION (Base.A)"));
            bad(version, body.replace("(EXTERNAL) Base.A", "ANYCLASS RESTRICTION (Base.A)"), "REFERENCE_EXTERNAL");
            ok(version, body.replace("TOPIC T = DEPENDS ON Base;", "TOPIC T EXTENDS Base =").replace("(EXTERNAL) Base.A", "A"));
            if (version.equals("2.4")) bad(version, body.replace("(EXTERNAL) ", "").replace("r: REFERENCE", "r: LIST OF REFERENCE"), "REFERENCE_EXTERNAL");
        }
    }

    @Test void mixedModelVersionsAllowReferencesToClassesWithoutOid() {
        for (String sourceVersion : List.of("2.3", "2.4")) {
            for (String targetVersion : List.of("2.3", "2.4")) {
                var target = CompilerTestSupport.source("memory:/target.ili", model(targetVersion,
                        "TOPIC T = CLASS A = NO OID; END A; END T;").replace("MODEL F ", "MODEL Base ").replace("END F.", "END Base."));
                var source = CompilerTestSupport.source("memory:/source.ili", model(sourceVersion,
                        "IMPORTS Base; TOPIC T = DEPENDS ON Base.T; STRUCTURE S = r: REFERENCE TO (EXTERNAL) Base.T.A; END S; END T;"));
                var result = CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                        Map.of(source.id(), source, target.id(), target), List.of(CompilerTestSupport.binding(CompilerTestSupport.parse(source), "Base", target, CompilerTestSupport.parse(target))), CompilerOptions.defaults()), CancellationToken.none());
                assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                        () -> sourceVersion + " -> " + targetVersion + " " + result);
            }
        }
    }

    @Test void referenceTypeQueriesAndOverridesUseTargetSets() {
        String body="TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; CLASS B EXTENDS A = END B; "
                + "STRUCTURE S = r: REFERENCE TO A; END S; STRUCTURE C EXTENDS S = r (EXTENDED): REFERENCE TO A RESTRICTION (B); END C; END T;";
        for(String version:List.of("2.3","2.4")) {
            var graph=ok(version,body).schema().semanticGraph();
            var refs=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).toList();
            var broad=refs.stream().filter(a -> a.header().owner().orElseThrow().equals(named(graph,"S").id())).findFirst().orElseThrow();
            var narrow=refs.stream().filter(a -> a.header().owner().orElseThrow().equals(named(graph,"C").id())).findFirst().orElseThrow();
            assertEquals(RelationDecision.ALLOWED,new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(),narrow.declaredType()),new TypeRef(graph.snapshotId(),broad.declaredType()),AssignmentContext.NORMAL).known().orElseThrow().decision());
            assertEquals(RelationDecision.DISALLOWED,new TypeRelations(graph).canAssign(new TypeRef(graph.snapshotId(),broad.declaredType()),new TypeRef(graph.snapshotId(),narrow.declaredType()),AssignmentContext.NORMAL).known().orElseThrow().decision());
        }
    }
}
