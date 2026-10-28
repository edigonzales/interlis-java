package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.model.query.TypeRelations;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleTargetInheritanceContractTest {
    private static CompilationOutcome compile(String version, String body) {
        var source = CompilerTestSupport.source("memory:/targets.ili", "INTERLIS " + version
                + "; CONTRACTED MODEL F AT \"x\" VERSION \"1\" = " + body + " END F.");
        return CompilerEngines.create().compile(CompilerTestSupport.request(List.of("F"),
                Map.of(source.id(), source), List.of(), CompilerOptions.defaults()), CancellationToken.none());
    }
    private static SemanticGraph succeeds(String version, String body) {
        var result = compile(version, body);
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result, () -> body + "\n" + (result instanceof CompilationOutcome.Rejected r ? r.analysis().diagnostics() : result))
                .schema().semanticGraph();
    }
    private static void rejects(String version, String body) {
        var result = assertInstanceOf(CompilationOutcome.Rejected.class, compile(version, body), body);
        assertFalse(result.rejection().reasons().contains(RejectionReason.INCOMPLETE_SUPPORT), result.toString());
    }
    private static Symbol named(SemanticGraph graph, String name) {
        return graph.symbols().stream().filter(s -> s.name().value().equals(name)).findFirst().orElseThrow();
    }
    private static String targets(String target, String expression) {
        return """
                TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = flag: BOOLEAN; END A; CLASS A1 EXTENDS A = END A1;
                  CLASS B = END B; CLASS B1 EXTENDS B = END B1; CLASS Owner = END Owner;
                  FUNCTION allKinds(v: OBJECTS OF ANYCLASS): BOOLEAN;
                  FUNCTION onlyA(v: OBJECTS OF A): BOOLEAN;
                  ASSOCIATION Link = target -- {1} %s; owner -- {1} Owner; END Link;
                  CONSTRAINTS OF Owner = MANDATORY CONSTRAINT %s; END;
                END T;
                """.formatted(target, expression);
    }
    private static String inheritance(String sourceModifiers, String target, String cardinality, String expression) {
        return """
                TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = flag: BOOLEAN; END A; CLASS A1 EXTENDS A = extra: BOOLEAN; END A1;
                  CLASS B = END B; CLASS Owner = END Owner; CLASS Child EXTENDS Owner = END Child;
                  FUNCTION onlyA1(v: OBJECT OF A1): BOOLEAN;
                  ASSOCIATION Link = target -- {1} A; owner -- {1} Owner; END Link;
                  ASSOCIATION Narrow EXTENDS Link =
                    target (EXTENDED) -- %s %s;
                    owner (%s) -- Child;
                  END Narrow;
                  CONSTRAINTS OF Child = MANDATORY CONSTRAINT %s; END;
                END T;
                """.formatted(cardinality, target, sourceModifiers, expression);
    }

    @Test void alternativesRestrictionsTypesAndReferenceRangesAreRetained() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = succeeds(version, targets("A RESTRICTION (A1) OR B RESTRICTION (B1)", "allKinds(target)"));
            var role = (RoleSymbol) named(graph, "target");
            assertEquals(List.of("A", "B"), role.targets().stream().map(t -> graph.symbol(t.base()).orElseThrow().name().value()).toList());
            assertEquals(List.of("A1", "B1"), role.targetRoots().stream().map(t -> graph.symbol(t).orElseThrow().name().value()).toList());
            var access = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast)
                    .flatMap(p -> p.path().steps().stream()).filter(PathStep.AssociationAccessStep.class::isInstance)
                    .map(PathStep.AssociationAccessStep.class::cast).findFirst().orElseThrow();
            var type = (ObjectArgumentType) graph.type(access.resultType()).orElseThrow();
            assertEquals(role.targetRoots(), type.restrictions());
            assertFalse(type.objects());
            for (var target : role.targets()) {
                for (SymbolId id : List.of(target.base(), target.restrictions().getFirst())) {
                    assertTrue(graph.references().stream().filter(r -> r.usage() == ReferenceUsage.ROLE_TARGET)
                            .flatMap(r -> r.segments().stream()).anyMatch(s -> s.target().filter(id::equals).isPresent()));
                }
            }
            succeeds(version, targets("ANYCLASS RESTRICTION (A1; B1)", "allKinds(target->THIS)"));
            succeeds(version, targets("A RESTRICTION (A1)", "onlyA(target)"));
            succeeds(version, targets("ANYCLASS RESTRICTION (A; A1)", "target->flag"));
            rejects(version, targets("A OR B", "onlyA(target)"));
            rejects(version, targets("A OR B", "target->flag"));
            rejects(version, targets("A", "DEFINED(target->flag->THIS)"));
            for (String bad : List.of("A OR A1", "A1 OR A", "A OR A", "ANYCLASS OR A", "A RESTRICTION (B)", "Missing", "Wrong.A")) {
                rejects(version, targets(bad, "#true"));
            }
        }
    }

    @Test void inheritedRolesKeepOrderOriginAndCardinality() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = succeeds(version, inheritance("EXTENDED", "A1", "", "DEFINED(target)")
                    .replace("owner (EXTENDED) -- Child;", "")
                    .replace("CONSTRAINTS OF Child", "ASSOCIATION Third EXTENDS Narrow = END Third; CONSTRAINTS OF Child"));
            var narrow = (AssociationSymbol) named(graph, "Narrow");
            var third = (AssociationSymbol) named(graph, "Third");
            assertEquals(named(graph, "Link").id(), narrow.baseAssociation().orElseThrow());
            var members = graph.effectiveMembers(third.id());
            assertEquals(List.of("target", "owner"), members.stream().map(m -> graph.symbol(m.declaredSymbol()).orElseThrow().name().value()).toList());
            assertEquals(List.of(Cardinality.exactly(1), Cardinality.exactly(1)), members.stream().map(m -> ((RoleSymbol) graph.symbol(m.declaredSymbol()).orElseThrow()).cardinality()).toList());
            assertTrue(members.stream().allMatch(m -> !m.inheritancePath().isEmpty()));
        }
    }

    @Test void associationMultiplicityIsInheritedAndRemainsAvailableInTheSchema() {
        for (String version : List.of("2.3", "2.4")) {
            String body = inheritance("EXTENDED, HIDING", "A1", "", "INTERLIS.objectCount(\\target) > 0")
                    .replace("ASSOCIATION Link =", "ASSOCIATION Link (OID) =")
                    .replace("owner -- {1} Owner; END Link;", "owner -- {1} Owner; CARDINALITY = {1..3}; END Link;");
            var graph = succeeds(version, body);
            var narrow = (AssociationSymbol) named(graph, "Narrow");
            assertEquals(((AssociationSymbol) named(graph, "Link")).cardinality(), narrow.cardinality());
            var access = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast)
                    .flatMap(p -> p.path().steps().stream()).filter(PathStep.AssociationAccessStep.class::isInstance)
                    .map(PathStep.AssociationAccessStep.class::cast).findFirst().orElseThrow();
            assertEquals(narrow.cardinality(), access.cardinality());
            rejects(version, body.replace("END Narrow;", "CARDINALITY = {1..4}; END Narrow;"));
        }
    }

    @Test void hidingNarrowsOnlyTheAccessFromTheAffectedParticipant() {
        for (String version : List.of("2.3", "2.4")) {
            var graph = succeeds(version, inheritance("EXTENDED, HIDING", "A1", "", "onlyA1(target)"));
            var path = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast).findFirst().orElseThrow();
            var access = (PathStep.AssociationAccessStep) path.path().steps().getFirst();
            assertEquals(named(graph, "Narrow").id(), access.association());
            var function = (FunctionSymbol) named(graph, "onlyA1");
            assertEquals(RelationDecision.ALLOWED, new TypeRelations(graph).canAssign(
                    new TypeRef(graph.snapshotId(), path.typeId()), new TypeRef(graph.snapshotId(), function.parameters().getFirst().typeId()),
                    AssignmentContext.NORMAL).known().orElseThrow().decision());
            rejects(version, inheritance("EXTENDED", "A1", "", "onlyA1(target)"));
            succeeds(version, inheritance("EXTENDED", "A1", "", "target->flag"));
            rejects(version, inheritance("EXTENDED, HIDING", "A1", "", "onlyA1(target)").replace("CONSTRAINTS OF Child", "CONSTRAINTS OF Owner"));
        }
    }

    @Test void rejectsWideningAddedRolesFinalAndHidingConflicts() {
        for (String version : List.of("2.3", "2.4")) {
            String valid = inheritance("EXTENDED, HIDING", "A1", "", "#true");
            rejects(version, valid.replace("EXTENDS Link", "EXTENDS Wrong.Link"));
            rejects(version, inheritance("EXTENDED, HIDING", "B", "", "#true"));
            rejects(version, inheritance("EXTENDED, HIDING", "A1", "{*}", "#true"));
            rejects(version, valid.replace("ASSOCIATION Link =", "ASSOCIATION Link (FINAL) ="));
            rejects(version, valid.replace("target -- {1} A;", "target (FINAL) -- {1} A;"));
            rejects(version, valid.replace("target (EXTENDED)", "target"));
            rejects(version, valid.replace("owner (EXTENDED, HIDING)", "owner (EXTENDED, HIDING, EXTERNAL)"));
            rejects(version, valid.replace("END Narrow;", "extra -- A; END Narrow;"));
            rejects(version, valid.replace("CONSTRAINTS OF Child", "ASSOCIATION Sibling EXTENDS Link = owner (EXTENDED) -- Child; END Sibling; CONSTRAINTS OF Child"));
            succeeds(version, inheritance("EXTENDED", "A1", "", "target->flag")
                    .replace("CONSTRAINTS OF Child", "ASSOCIATION Sibling EXTENDS Link = owner (EXTENDED) -- Child; END Sibling; CONSTRAINTS OF Child"));
            rejects(version, "TOPIC T = OID AS INTERLIS.UUIDOID; STRUCTURE A = END A; CLASS B = END B; ASSOCIATION Link = a -- A; b -- B; END Link; END T;");
            rejects(version, "TOPIC T = OID AS INTERLIS.UUIDOID; ASSOCIATION A EXTENDS B = END A; ASSOCIATION B EXTENDS A = END B; END T;");
            rejects(version, "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A (EXTENDED) = END A; END T;");
            rejects(version, "TOPIC T = OID AS INTERLIS.UUIDOID; CLASS A = END A; ASSOCIATION Link = only -- A; END Link; END T;");
        }
    }

    @Test void externalConstraintsUseTheirLexicalExtendedTopic() {
        for (String version : List.of("2.3", "2.4")) {
            String body = """
                    TOPIC Base = OID AS INTERLIS.UUIDOID; CLASS A = END A; CLASS B = END B;
                      ASSOCIATION Link = a -- {1} A; b -- {1} B; END Link;
                    END Base;
                    TOPIC Local EXTENDS Base = CLASS A (EXTENDED) = flag: BOOLEAN; END A;
                      CONSTRAINTS OF B = MANDATORY CONSTRAINT a->flag; END;
                    END Local;
                    """;
            var graph = succeeds(version, body);
            var path = graph.expressions().stream().filter(PathExpression.class::isInstance).map(PathExpression.class::cast).findFirst().orElseThrow();
            var attr = (PathStep.AttributeStep) path.path().steps().getLast();
            assertEquals("flag", graph.symbol(attr.target()).orElseThrow().name().value());
            rejects(version, body.replace("CLASS A (EXTENDED)", "CLASS Unrelated"));
            succeeds(version, body.replace("CONSTRAINTS OF B", "ASSOCIATION Link (EXTENDED) = END Link; CONSTRAINTS OF B"));
        }
    }
}
