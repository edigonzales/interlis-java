package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjectFunctionContractTest {
    private static CompilationOutcome compile(String version, String argumentType,
                                               String target, String expression) {
        var source = CompilerTestSupport.source("memory:/object-functions.ili",
                "INTERLIS " + version + "; CONTRACTED MODEL F AT \"x\" VERSION \"1\" = "
                + "TOPIC T = CLASS A = value: 0..10; END A; "
                + "CLASS B EXTENDS A = END B; CLASS Other = END Other; "
                + "FUNCTION f(v: " + argumentType + "): BOOLEAN; "
                + "FUNCTION identity(v: OBJECT OF A): OBJECT OF A; "
                + "CONSTRAINTS OF " + target + " = MANDATORY CONSTRAINT "
                + expression + "; END; END T; END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                CancellationToken.none());
    }

    @Test
    void acceptsObjectsAndSubclassesButRejectsScalarsUnrelatedObjectsAndSets() {
        for (String version : List.of("2.3", "2.4")) {
            for (String type : List.of("OBJECT OF A", "OBJECT OF F.T.A", "OBJECT OF ANYCLASS",
                    "OBJECT OF A RESTRICTION (B)", "OBJECT OF ANYCLASS RESTRICTION (B)")) {
                assertInstanceOf(CompilationOutcome.Succeeded.class,
                        compile(version, type, "B", "f(THIS)"), type);
            }
            assertInstanceOf(CompilationOutcome.Succeeded.class,
                    compile(version, "OBJECT OF A", "B", "f(identity(THIS))"));
            assertInstanceOf(CompilationOutcome.Succeeded.class,
                    compile(version, "OBJECT OF A", "B", "f(THIS->THIS)"));
            assertInstanceOf(CompilationOutcome.Succeeded.class,
                    compile(version, "OBJECT OF A", "B", "identity(THIS) == THIS"));
            for (String expression : List.of("f(1)", "f(#true)", "f(ALL)",
                    "f(value->THIS)", "identity(THIS) < THIS")) {
                var rejected = assertInstanceOf(CompilationOutcome.Rejected.class,
                        compile(version, "OBJECT OF A", "A", expression), expression);
                assertFalse(rejected.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT));
            }
            for (String type : List.of("OBJECT OF Other", "OBJECT OF A RESTRICTION (B)",
                    "OBJECT OF Missing", "OBJECT OF Wrong.A",
                    "OBJECT OF A RESTRICTION (Other)", "OBJECT OF A RESTRICTION (Missing)")) {
                assertInstanceOf(CompilationOutcome.Rejected.class,
                        compile(version, type, "A", "f(THIS)"), type);
            }
        }
    }

    @Test
    void retainsBoundSignatureRestrictionsReferencesAndSharedQueryResult() {
        var success = assertInstanceOf(CompilationOutcome.Succeeded.class,
                compile("2.4", "OBJECT OF A RESTRICTION (B)", "B", "f(THIS)"));
        var graph = success.analysis().semanticGraph();
        var function = graph.symbols().stream().filter(FunctionSymbol.class::isInstance)
                .map(FunctionSymbol.class::cast).filter(f -> f.name().value().equals("f"))
                .findFirst().orElseThrow();
        var formal = assertInstanceOf(ObjectArgumentType.class,
                graph.type(function.parameters().getFirst().typeId()).orElseThrow());
        assertEquals("A", graph.symbol(formal.base().orElseThrow()).orElseThrow().name().value());
        assertEquals("B", graph.symbol(formal.restrictions().getFirst()).orElseThrow().name().value());
        assertThrows(UnsupportedOperationException.class, () -> formal.restrictions().clear());
        var call = graph.expressions().stream().filter(FunctionCallExpression.class::isInstance)
                .map(FunctionCallExpression.class::cast).findFirst().orElseThrow();
        var actual = graph.expression(call.children().getFirst()).orElseThrow();
        assertEquals(RelationDecision.ALLOWED, new TypeRelations(graph).canAssign(
                new TypeRef(graph.snapshotId(), actual.typeId()),
                new TypeRef(graph.snapshotId(), formal.id()), AssignmentContext.NORMAL)
                .known().orElseThrow().decision());
        assertTrue(graph.references().stream().anyMatch(r -> r.resolution().equals(new BindingResolution.BoundTarget(formal.base().orElseThrow()))));
        assertTrue(graph.references().stream().anyMatch(r ->
                r.resolution().equals(new BindingResolution.BoundTarget(formal.restrictions().getFirst()))));
        var identity = graph.symbols().stream().filter(FunctionSymbol.class::isInstance)
                .map(FunctionSymbol.class::cast).filter(f -> f.name().value().equals("identity"))
                .findFirst().orElseThrow();
        var queries = new TypeRelations(graph);
        for (var operator : ComparisonOperator.values()) {
            assertEquals(operator == ComparisonOperator.EQUALITY
                            ? RelationDecision.ALLOWED : RelationDecision.DISALLOWED,
                    queries.areComparable(new TypeRef(graph.snapshotId(), identity.returnType()),
                            new TypeRef(graph.snapshotId(), actual.typeId()), operator)
                            .known().orElseThrow().decision());
        }
    }

    @Test
    void classConstraintsSeeTheirOwnBaseBeforeMembersAreBuilt() {
        var source = CompilerTestSupport.source("memory:/derived.ili", """
                INTERLIS 2.4; MODEL F AT "x" VERSION "1" =
                TOPIC T =
                  CLASS A = END A;
                  FUNCTION f(v: OBJECT OF A): BOOLEAN;
                  CLASS B EXTENDS A =
                    MANDATORY CONSTRAINT f(THIS);
                  END B;
                END T; END F.
                """);
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                        Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                        CancellationToken.none()));
    }

    @Test
    void doesNotBindAnUnimportedModelOrADistantShadowedClass() {
        for (String type : List.of("Hidden.A", "A")) {
            var source = CompilerTestSupport.source("memory:/scopes.ili", """
                    INTERLIS 2.4;
                    MODEL Hidden AT "x" VERSION "1" = CLASS A = END A; END Hidden.
                    MODEL F AT "x" VERSION "1" =
                      CLASS A = END A;
                      TOPIC T =
                        CLASS A = END A;
                        FUNCTION f(v: OBJECT OF %s): BOOLEAN;
                        CONSTRAINTS OF A = MANDATORY CONSTRAINT f(THIS); END;
                      END T;
                    END F.
                    """.formatted(type));
            var outcome = CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                    Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                    CancellationToken.none());
            if (type.equals("Hidden.A")) assertInstanceOf(CompilationOutcome.Rejected.class, outcome);
            else assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
        }
    }

    @Test
    void anAssociationRoleSelectsOneParticipantRegardlessOfRelationshipCardinality() {
        for (String cardinality : List.of("1", "*")) {
            var source = CompilerTestSupport.source("memory:/role-function.ili", """
                    INTERLIS 2.4; MODEL F AT "x" VERSION "1" =
                    TOPIC T = OID AS INTERLIS.UUIDOID;
                      CLASS A = END A;
                      FUNCTION f(v: OBJECT OF A): BOOLEAN;
                      ASSOCIATION Link =
                        a -- {%s} A;
                        b -- {1} A;
                        MANDATORY CONSTRAINT f(a);
                      END Link;
                    END T; END F.
                    """.formatted(cardinality));
            var outcome = CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                    Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                    CancellationToken.none());
            assertInstanceOf(CompilationOutcome.Succeeded.class, outcome);
        }
    }

    @Test
    void thisAfterAnObjectStepKeepsThatObjectInsteadOfResettingThePath() {
        var source = CompilerTestSupport.source("memory:/this-path.ili", """
                INTERLIS 2.4; MODEL F AT "x" VERSION "1" =
                TOPIC T = OID AS INTERLIS.UUIDOID;
                  CLASS B = END B;
                  FUNCTION f(v: OBJECT OF B): BOOLEAN;
                  CLASS A =
                    link: REFERENCE TO B;
                    MANDATORY CONSTRAINT f(link->THIS);
                  END A;
                END T; END F.
                """);
        assertInstanceOf(CompilationOutcome.Succeeded.class,
                CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                        Map.of(source.id(), source), List.of(), CompilerOptions.defaults()),
                        CancellationToken.none()));
    }
}
