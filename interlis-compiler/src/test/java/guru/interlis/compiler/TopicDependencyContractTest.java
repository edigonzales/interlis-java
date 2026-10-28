package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopicDependencyContractTest {
    private static final String TARGETS = "TOPIC A = OID AS INTERLIS.UUIDOID; CLASS Target = END Target; END A; "
            + "TOPIC B = OID AS INTERLIS.UUIDOID; CLASS Other = END Other; END B; ";
    private static String model(String version, String body) {
        return "INTERLIS " + version + "; CONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\" = " + body + " END F.";
    }
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/depends.ili", model(version, body));
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"), Map.of(source.id(),source), List.of(), CompilerOptions.defaults()), CancellationToken.none());
    }
    private static CompilationOutcome.Succeeded ok(String version, String body) {
        var result = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> body + "\n" + ((CompilationOutcome.Rejected)result).analysis().diagnostics());
    }
    private static void bad(String version, String body, String reason) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
        assertTrue(result.analysis().diagnostics().toString().contains(reason), () -> result.analysis().diagnostics().toString());
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
    }
    private static String reference(String target) {
        return "STRUCTURE S = ref: REFERENCE TO (EXTERNAL) " + target + "; END S; ";
    }
    private static String role(String target) {
        return "CLASS Owner = OID AS INTERLIS.UUIDOID; END Owner; ASSOCIATION Link = "
                + "ref (EXTERNAL) -- " + target + "; owner -- Owner; END Link; ";
    }

    @Test void externalDoesNotReplaceDirectDependenciesInEitherVersion() {
        for (String version : List.of("2.3", "2.4")) for (String member : List.of(reference("F.A.Target"), role("F.A.Target"))) {
            String body = TARGETS + "TOPIC T = " + member + "END T;";
            bad(version, body, "REFERENCE_DEPENDS_ON");
            ok(version, body.replace("TOPIC T =", "TOPIC T = DEPENDS ON A;"));
            bad(version, body.replace("TOPIC T =", "TOPIC T = DEPENDS ON B;"), "REFERENCE_DEPENDS_ON");
            bad(version, body.replace("TOPIC T =", "TOPIC T = DEPENDS ON A;").replace("(EXTERNAL)", ""), "EXTERNAL");
        }
    }

    @Test void allRestrictionsAndRoleAlternativesAreChecked() {
        for (String version : List.of("2.3", "2.4")) for (String member : List.of(
                reference("ANYCLASS RESTRICTION (F.A.Target; F.B.Other)"),
                role("ANYCLASS RESTRICTION (F.A.Target; F.B.Other)"), role("F.A.Target OR F.B.Other"))) {
            String body = TARGETS + "TOPIC T = DEPENDS ON A; " + member + "END T;";
            bad(version, body, "REFERENCE_DEPENDS_ON");
            ok(version, body.replace("DEPENDS ON A;", "DEPENDS ON A,B;"));
        }
        for (String version : List.of("2.3", "2.4")) for (String member : List.of(reference("F.A.Target RESTRICTION (F.D.Child)"), role("F.A.Target RESTRICTION (F.D.Child)"))) {
            String body = TARGETS + "TOPIC D EXTENDS A = CLASS Child EXTENDS Target = END Child; END D; "
                    + "TOPIC T = DEPENDS ON D; " + member + "END T;";
            ok(version, body);
            bad(version, body.replace("DEPENDS ON D;", "DEPENDS ON A;"), "REFERENCE_DEPENDS_ON");
        }
    }

    @Test void sourceTopicInheritanceRetainsDependenciesButNotTransitively() {
        for (String version : List.of("2.3", "2.4")) for (String member : List.of(reference("F.A.Target"), role("F.A.Target"))) {
            String body = TARGETS + "TOPIC Base = DEPENDS ON A; END Base; TOPIC Middle EXTENDS Base = END Middle; "
                    + "TOPIC T EXTENDS Middle = " + member + "END T;";
            ok(version, body);
            bad(version, body.replace("TOPIC T EXTENDS Middle =", "TOPIC T = DEPENDS ON Middle;"), "REFERENCE_DEPENDS_ON");
            ok(version, body.replace("TOPIC T EXTENDS Middle =", "TOPIC T = DEPENDS ON Middle,A;"));
        }
    }

    @Test void inheritedMembersAreCheckedInTheirEffectiveOwnerTopic() {
        for (String version : List.of("2.3", "2.4")) {
            String body = TARGETS + "TOPIC Base = DEPENDS ON A; " + reference("F.A.Target") + "END Base; "
                    + "TOPIC T = STRUCTURE Child EXTENDS F.Base.S = END Child; END T;";
            bad(version, body, "REFERENCE_DEPENDS_ON");
            ok(version, body.replace("TOPIC T =", "TOPIC T = DEPENDS ON A;"));
            ok(version, body.replace("TOPIC T =", "TOPIC T EXTENDS Base ="));
            String roles = TARGETS + "TOPIC Base = DEPENDS ON A; " + role("F.A.Target") + "END Base; "
                    + "TOPIC T EXTENDS Base = ASSOCIATION Link (EXTENDED) = END Link; END T;";
            ok(version, roles);
            bad(version, roles.replace("DEPENDS ON A;", "DEPENDS ON B;"), "REFERENCE_DEPENDS_ON");
        }
    }

    @Test void inheritedTargetsUseIdentityNotEqualNames() {
        for (String version : List.of("2.3", "2.4")) {
            String body = TARGETS + "TOPIC T EXTENDS A = " + reference("Target").replace("(EXTERNAL) ", "") + "END T;";
            ok(version, body);
            String collision = body.replace("CLASS Other", "CLASS Target").replace("END Other", "END Target")
                    .replace("REFERENCE TO Target", "REFERENCE TO F.B.Target");
            bad(version, collision, "REFERENCE_DEPENDS_ON");
            bad(version, collision.replace("TOPIC T EXTENDS A =", "TOPIC T EXTENDS A = DEPENDS ON B;"), "REFERENCE_EXTERNAL");
            ok(version, collision.replace("TOPIC T EXTENDS A =", "TOPIC T EXTENDS A = DEPENDS ON B;").replace("REFERENCE TO F.B.Target", "REFERENCE TO (EXTERNAL) F.B.Target"));
        }
    }

    @Test void referencesInNestedStructuresAreCheckedAtTheirUseTopic() {
        for (String version : List.of("2.3", "2.4")) {
            String body = TARGETS + "STRUCTURE Shared = ref: REFERENCE TO (EXTERNAL) F.A.Target; END Shared; "
                    + "STRUCTURE Box = items: BAG OF Shared; END Box; "
                    + "TOPIC T = CLASS Holder = value: Box; END Holder; END T;";
            bad(version, body, "REFERENCE_DEPENDS_ON");
            ok(version, body.replace("TOPIC T =", "TOPIC T = DEPENDS ON A;"));
        }
    }

    @Test void sameNamedImportedTopicsDoNotSatisfyEachOthersDependencies() {
        for (String version : List.of("2.3", "2.4")) {
            var target = CompilerTestSupport.source("memory:/target.ili", model(version,
                    "TOPIC A = OID AS INTERLIS.UUIDOID; CLASS Target = END Target; END A;").replace("MODEL F ", "MODEL TargetModel ").replace("END F.", "END TargetModel."));
            var other = CompilerTestSupport.source("memory:/other.ili", model(version,
                    "TOPIC A = END A;").replace("MODEL F ", "MODEL OtherModel ").replace("END F.", "END OtherModel."));
            for (String dependency : List.of("TargetModel.A", "OtherModel.A")) {
                var source = CompilerTestSupport.source("memory:/source.ili", model(version,
                        "IMPORTS TargetModel,OtherModel; TOPIC T = DEPENDS ON " + dependency + "; " + reference("TargetModel.A.Target") + "END T;"));
                var syntax = CompilerTestSupport.parse(source);
                var bindings = List.of(CompilerTestSupport.binding(syntax, "TargetModel", target, CompilerTestSupport.parse(target)),
                        CompilerTestSupport.binding(syntax, "OtherModel", other, CompilerTestSupport.parse(other)));
                var outcome = CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                        Map.of(source.id(),source,target.id(),target,other.id(),other),bindings,CompilerOptions.defaults()),CancellationToken.none());
                if (dependency.equals("TargetModel.A")) assertInstanceOf(CompilationOutcome.Succeeded.class,outcome);
                else assertTrue(assertInstanceOf(CompilationOutcome.Rejected.class,outcome).analysis().diagnostics().toString().contains("REFERENCE_DEPENDS_ON"));
            }
        }
    }

    @Test void mandatoryOnlyOverrideRetainsTheBoundReferenceType() {
        for (String version : List.of("2.3", "2.4")) {
            String body = TARGETS + "TOPIC Base = DEPENDS ON A; " + reference("F.A.Target") + "END Base; "
                    + "TOPIC T = STRUCTURE Child EXTENDS F.Base.S = ref (EXTENDED): MANDATORY; END Child; END T;";
            bad(version, body, "REFERENCE_DEPENDS_ON");
            var graph = ok(version, body.replace("TOPIC T =", "TOPIC T = DEPENDS ON A;")).schema().semanticGraph();
            var child = graph.symbols().stream().filter(s -> s.name().value().equals("Child")).findFirst().orElseThrow();
            var attribute = (AttributeSymbol)graph.symbol(graph.effectiveMembers(child.id()).getFirst().declaredSymbol()).orElseThrow();
            assertTrue(attribute.mandatory());
            assertInstanceOf(ReferenceType.class, graph.type(attribute.declaredType()).orElseThrow());
        }
    }

    @Test void cyclesAndUnresolvedDependencyNamesAreRejected() {
        for (String version : List.of("2.3", "2.4")) {
            for (String body : List.of("TOPIC A = DEPENDS ON A; END A;", "TOPIC A = DEPENDS ON B; END A; TOPIC B = DEPENDS ON A; END B;",
                    "TOPIC A = DEPENDS ON B; END A; TOPIC B EXTENDS A = END B;"))
                bad(version, body, "TOPIC_DEPENDENCY_CYCLE");
            bad(version, TARGETS + "TOPIC T = DEPENDS ON Wrong.A; END T;", "unresolved");
            bad(version, TARGETS + "TOPIC T = DEPENDS ON A.Target; END T;", "unresolved");
            ok(version, TARGETS + "TOPIC T = DEPENDS ON A,B; END T;");
        }
    }

    @Test void collectionsAndBoundDependencyReferencesRemainInspectable() {
        String body = TARGETS + "TOPIC T = DEPENDS ON F.A; " + reference("F.A.Target").replace("ref: REFERENCE", "ref: LIST OF REFERENCE") + "END T;";
        var result = ok("2.4", body); var graph = result.schema().semanticGraph();
        var topic = graph.symbols().stream().filter(TopicSymbol.class::isInstance).map(TopicSymbol.class::cast).filter(t -> t.name().value().equals("T")).findFirst().orElseThrow();
        assertEquals(1,topic.dependencies().size());
        var source = CompilerTestSupport.source("memory:/depends.ili", model("2.4",body));
        int offset = source.text().indexOf("DEPENDS ON F.A") + "DEPENDS ON F.".length();
        var hit = LanguageQueries.create().symbolAt(result.analysis(),source.range(offset,offset),CancellationToken.none()).known().orElseThrow();
        assertEquals(topic.dependencies().getFirst(),hit.symbol());
        bad("2.4", body.replace("DEPENDS ON F.A;", ""), "REFERENCE_DEPENDS_ON");
    }
}
