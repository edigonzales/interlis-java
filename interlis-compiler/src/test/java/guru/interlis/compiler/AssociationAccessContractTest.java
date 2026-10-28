package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssociationAccessContractTest {
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/roles.ili", "INTERLIS " + version
                + "; CONTRACTED MODEL F AT \"x\" VERSION \"1\" = " + body + " END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults()), CancellationToken.none());
    }
    private static String model(String cardinality, String expression) {
        return """
                TOPIC T =
                  CLASS A = OID AS INTERLIS.UUIDOID; flag: BOOLEAN; END A;
                  CLASS B EXTENDS A = END B;
                  CLASS Other = OID AS INTERLIS.UUIDOID; END Other;
                  CLASS Owner = OID AS INTERLIS.UUIDOID; END Owner;
                  FUNCTION single(v: OBJECT OF A): BOOLEAN;
                  FUNCTION many(v: OBJECTS OF A): BOOLEAN;
                  FUNCTION restricted(v: OBJECTS OF A RESTRICTION (B)): BOOLEAN;
                  FUNCTION result(v: OBJECTS OF A): OBJECTS OF A;
                  ASSOCIATION Link = a -- {%s} A; owner -- {1} Owner; flag: BOOLEAN; END Link;
                  CONSTRAINTS OF Owner = MANDATORY CONSTRAINT %s; END;
                END T;
                """.formatted(cardinality, expression);
    }
    private static CompilationOutcome.Succeeded succeeds(String version, String body) {
        var outcome = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, outcome,
                () -> body + "\n" + outcome);
    }
    private static CompilationOutcome.Rejected rejects(String version, String body) {
        return assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
    }

    @Test void associationTargetsAnyClassAndInheritedTopicTargetsRemainBound() {
        for (String version : List.of("2.3", "2.4")) {
            var result = succeeds(version, """
                    TOPIC Base = OID AS INTERLIS.UUIDOID; CLASS A = END A; END Base;
                    TOPIC T EXTENDS Base =
                      CLASS B = END B;
                      ASSOCIATION Link = a -- {1} A; b -- {1} B; END Link;
                      ASSOCIATION Meta = link -- Link; any -- ANYCLASS; END Meta;
                    END T;
                    """);
            var graph = result.schema().semanticGraph();
            var role = graph.symbols().stream().filter(RoleSymbol.class::isInstance)
                    .map(RoleSymbol.class::cast).filter(r -> r.name().value().equals("link")).findFirst().orElseThrow();
            assertInstanceOf(AssociationSymbol.class, graph.symbol(role.targets().getFirst().base()).orElseThrow());
            var any = graph.symbols().stream().filter(RoleSymbol.class::isInstance)
                    .map(RoleSymbol.class::cast).filter(r -> r.name().value().equals("any")).findFirst().orElseThrow();
            assertInstanceOf(Origin.PredefinedOrigin.class, graph.symbol(any.targets().getFirst().base()).orElseThrow().origin());
        }
    }

    @Test void compiledSchemaKeepsSignaturesAndIntermediateAccessTypes() {
        var schema = succeeds("2.4", model("1", "\\a->flag")).schema().semanticGraph();
        for (var symbol : schema.symbols()) {
            if (symbol instanceof FunctionSymbol function) {
                assertTrue(schema.type(function.returnType()).isPresent());
                function.parameters().forEach(p -> assertTrue(schema.type(p.typeId()).isPresent()));
            }
        }
        var path = schema.expressions().stream().filter(PathExpression.class::isInstance)
                .map(PathExpression.class::cast).findFirst().orElseThrow();
        var access = assertInstanceOf(PathStep.AssociationAccessStep.class, path.path().steps().getFirst());
        assertTrue(schema.type(access.resultType()).isPresent());
        var repeated = succeeds("2.4", model("1", "INTERLIS.objectCount(\\a) > 0")
                .replace("ASSOCIATION Link =", "ASSOCIATION Link (OID) =")
                .replace("flag: BOOLEAN; END Link;", "flag: BOOLEAN; CARDINALITY = {2}; END Link;"));
        var access2 = repeated.schema().semanticGraph().expressions().stream().filter(PathExpression.class::isInstance)
                .map(PathExpression.class::cast).flatMap(p -> p.path().steps().stream())
                .filter(PathStep.AssociationAccessStep.class::isInstance).map(PathStep.AssociationAccessStep.class::cast)
                .findFirst().orElseThrow();
        rejects("2.4", model("1", "#true")
                .replace("flag: BOOLEAN; END Link;", "flag: BOOLEAN; CARDINALITY = {2}; END Link;"));
        rejects("2.4", model("1", "#true").replace("ASSOCIATION Link =", "ASSOCIATION Link = NO OID;")
                .replace("flag: BOOLEAN; END Link;", "flag: BOOLEAN; CARDINALITY = {2}; END Link;"));
        assertEquals(Cardinality.exactly(2), access2.cardinality());
        assertTrue(((ObjectArgumentType) repeated.schema().semanticGraph().type(access2.resultType()).orElseThrow()).objects());
    }

    @Test void distinguishesClassAccessFromDirectAssociationRoleAndRetainsCardinality() {
        for (String version : List.of("2.3", "2.4")) {
            for (String cardinality : List.of("1", "0..1", "*", "2..3")) {
                var graph = succeeds(version, model(cardinality, "many(a)")).analysis().semanticGraph();
                var access = graph.expressions().stream().filter(PathExpression.class::isInstance)
                        .map(PathExpression.class::cast).flatMap(p -> p.path().steps().stream())
                        .filter(PathStep.AssociationAccessStep.class::isInstance)
                        .map(PathStep.AssociationAccessStep.class::cast).findFirst().orElseThrow();
                assertEquals("Link", graph.symbol(access.association()).orElseThrow().name().value());
                assertEquals("owner", graph.symbol(access.sourceRole()).orElseThrow().name().value());
                assertEquals("a", graph.symbol(access.targetRole()).orElseThrow().name().value());
                assertFalse(access.associationObject());
                var type = assertInstanceOf(ObjectArgumentType.class, graph.type(access.resultType()).orElseThrow());
                assertEquals("A", graph.symbol(type.base().orElseThrow()).orElseThrow().name().value());
                assertEquals(cardinality.equals("*") || cardinality.equals("2..3"), type.objects());
                if (type.objects()) rejects(version, model(cardinality, "single(a)"));
                else succeeds(version, model(cardinality, "single(a)"));
                succeeds(version, model(cardinality, "INTERLIS.objectCount(a) >= 0"));
            }
            succeeds(version, model("1", "a->flag"));
            succeeds(version, model("1", "single(a->THIS)"));
            rejects(version, model("*", "single(a->THIS)"));
            rejects(version, model("*", "a == a"));
            rejects(version, model("*", "a->flag"));
            rejects(version, model("1", "single(\\a)"));
            rejects(version, model("1", "INTERLIS.objectCount(1) > 0"));
        }
    }

    @Test void associationOperatorKeepsAssociationTargetAndNavigableRoleRange() {
        var graph = succeeds("2.4", model("1", "\\a->flag")).analysis().semanticGraph();
        var path = graph.expressions().stream().filter(PathExpression.class::isInstance)
                .map(PathExpression.class::cast).findFirst().orElseThrow();
        var access = assertInstanceOf(PathStep.AssociationAccessStep.class, path.path().steps().getFirst());
        assertTrue(access.associationObject());
        var type = assertInstanceOf(ObjectArgumentType.class, graph.type(access.resultType()).orElseThrow());
        assertEquals(access.association(), type.base().orElseThrow());
        var attribute = assertInstanceOf(PathStep.AttributeStep.class, path.path().steps().getLast());
        assertEquals(access.association(), graph.symbol(attribute.target()).orElseThrow().header().owner().orElseThrow());
        assertTrue(graph.references().stream().anyMatch(r -> r.segments().stream().anyMatch(s ->
                s.target().equals(java.util.Optional.of(access.targetRole()))
                && s.range().startOffset() == access.range().startOffset() + 1)));
    }

    @Test void bindsAllSelectionsAndChecksRestrictionsWithoutRuntimeFiltering() {
        for (String version : List.of("2.3", "2.4")) {
            for (String expression : List.of("many(ALL(A))", "many(ALL(B))", "many(result(ALL(B)))",
                    "restricted(ALL(A RESTRICTION (B)))", "INTERLIS.objectCount(ALL) > 0")) {
                succeeds(version, model("1", expression));
            }
            for (String expression : List.of("many(ALL)", "many(ALL(Other))", "many(ALL(ANYCLASS))",
                    "many(ALL(Wrong.A))", "restricted(ALL(A))", "single(ALL(A))",
                    "result(ALL(A)) == result(ALL(A))", "many(ALL(A RESTRICTION (Other)))")) {
                rejects(version, model("1", expression));
            }
        }
        var graph = succeeds("2.4", model("1", "many(ALL(B))")).analysis().semanticGraph();
        var all = graph.expressions().stream().filter(ObjectSetExpression.class::isInstance)
                .map(ObjectSetExpression.class::cast).findFirst().orElseThrow();
        var actual = assertInstanceOf(ObjectArgumentType.class, graph.type(all.typeId()).orElseThrow());
        assertTrue(actual.objects());
        assertEquals("B", graph.symbol(actual.base().orElseThrow()).orElseThrow().name().value());
        var formal = graph.symbols().stream().filter(FunctionSymbol.class::isInstance)
                .map(FunctionSymbol.class::cast).filter(f -> f.name().value().equals("many"))
                .findFirst().orElseThrow().parameters().getFirst().typeId();
        assertEquals(RelationDecision.ALLOWED, new TypeRelations(graph).canAssign(
                new TypeRef(graph.snapshotId(), actual.id()), new TypeRef(graph.snapshotId(), formal),
                AssignmentContext.NORMAL).known().orElseThrow().decision());
        assertEquals(RelationDecision.DISALLOWED, new TypeRelations(graph).areComparable(
                new TypeRef(graph.snapshotId(), actual.id()), new TypeRef(graph.snapshotId(), actual.id()),
                ComparisonOperator.EQUALITY).known().orElseThrow().decision());
    }

    @Test void rejectsAmbiguousInvisibleAndExternalReverseAccesses() {
        String base = model("1", "single(a)");
        var ambiguous = rejects("2.4", base.replace("CONSTRAINTS OF Owner", """
                ASSOCIATION Second = a -- {1} A; owner2 -- {1} Owner; END Second;
                CONSTRAINTS OF Owner"""));
        assertTrue(ambiguous.analysis().diagnostics().stream().anyMatch(d -> d.code().value().equals("ILI-SEM-0002")));
        rejects("2.4", base.replace("owner --", "owner (EXTERNAL) --"));
        succeeds("2.4", base.replace("a --", "a (EXTERNAL) --"));
        rejects("2.4", base.replace("a -- {1} A", "a -- {1} Wrong.A"));
        rejects("2.4", """
                TOPIC Hidden = CLASS A = END A; CLASS Owner = END Owner;
                  ASSOCIATION Link = a -- {1} A; owner -- {1} Owner; END Link;
                END Hidden;
                TOPIC T = OID AS INTERLIS.UUIDOID; CLASS Owner = MANDATORY CONSTRAINT DEFINED(a); END Owner; END T;
                """);
        rejects("2.4", """
                TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = MANDATORY CONSTRAINT DEFINED(b); END A;
                CLASS B = END B;
                ASSOCIATION Link = a -- {1} A; b -- {1} B; END Link; END T;
                """);
    }

    @Test void inheritsAccessThroughClassIdentityAndHiding() {
        succeeds("2.4", model("1", "single(a)").replace("CONSTRAINTS OF Owner",
                "CLASS Child EXTENDS Owner = END Child; CONSTRAINTS OF Child"));
        var result = succeeds("2.4", model("1", "single(a)").replace("CONSTRAINTS OF Owner", """
                CLASS Child EXTENDS Owner = END Child;
                ASSOCIATION Derived EXTENDS Link =
                  a (EXTENDED) -- {1} B;
                  owner (EXTENDED, HIDING) -- {1} Child;
                END Derived;
                CONSTRAINTS OF Child"""));
        var graph = result.schema().semanticGraph();
        var access = graph.expressions().stream().filter(PathExpression.class::isInstance)
                .map(PathExpression.class::cast).flatMap(p -> p.path().steps().stream())
                .filter(PathStep.AssociationAccessStep.class::isInstance)
                .map(PathStep.AssociationAccessStep.class::cast).findFirst().orElseThrow();
        assertEquals("Derived", graph.symbol(access.association()).orElseThrow().name().value());
    }
}
