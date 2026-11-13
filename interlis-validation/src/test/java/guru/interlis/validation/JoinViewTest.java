package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class JoinViewTest {
    @Test
    void navigatesSecondAliasAcrossBasketsWithDifferentGenericBindings() {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                    A = COORD 0.0..100.0, 0.0..100.0;
                    B = COORD 200.00..300.00, 200.00..300.00;
                  CONTEXT Choice = G = A OR B;
                  TOPIC Data =
                    DEFERRED GENERICS G;
                    CLASS Group = number: 0..10; END Group;
                    STRUCTURE Link = group: REFERENCE TO (EXTERNAL) Group; END Link;
                    CLASS Item = number: 0..10; point: G; link: Link; END Item;
                    VIEW Pairs JOIN OF a~Item,b~Item;
                      WHERE a->number < b->number;
                      =
                        MANDATORY CONSTRAINT b->link->group->number == b->number;
                    END Pairs;
                  END Data;
                END Checks.
                """);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(
                ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var first = """
                <Data i:bid="first" i:domains="Checks.G=Checks.A">
                  <Item i:tid="one"><number>1</number><point><g:coord><g:c1>5.0</g:c1><g:c2>5.0</g:c2></g:coord></point>
                    <link><Link><group i:ref="g1"/></Link></link></Item>
                  <Group i:tid="g1"><number>1</number></Group>
                </Data>
                """;
        var second = """
                <Data i:bid="second" i:domains="Checks.G=Checks.B">
                  <Item i:tid="two"><number>2</number><point><g:coord><g:c1>205.00</g:c1><g:c2>205.00</g:c2></g:coord></point>
                    <link><Link><group i:ref="g2"/></Link></link></Item>
                  <Group i:tid="g2"><number>%d</number></Group>
                </Data>
                """;
        for (int target : List.of(2, 3)) {
            var last = second.formatted(target);
            for (var baskets : List.of(first + last, last + first)) {
                var report = validate(plan, List.of("Item", "Group"), primary("p", baskets));
                assertEquals(target == 2 ? List.of() : List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE),
                        report.examples().stream().map(ValidationDiagnostic::code).toList());
                assertEquals(1, report.coverage().constraints().getFirst().occurrences());
                assertEquals(ValidationCoverage.ConstraintStatus.CHECKED,
                        report.coverage().constraints().getFirst().status());
            }
        }
    }

    @Test
    void navigatesRelationshipsFromTheSecondSelfJoinAlias() {
        var plan = plan("""
                CLASS Item = number: 0..10; END Item;
                CLASS Group = number: 0..10; END Group;
                ASSOCIATION Membership =
                  group -- {1} Group;
                  items -- {0..*} Item;
                END Membership;
                VIEW Pairs JOIN OF a~Item,b~Item;
                  WHERE a->number < b->number;
                  =
                    MANDATORY CONSTRAINT b->group->number == 1;
                END Pairs;
                """);
        var first = "<Item i:tid='one'><number>1</number><group i:ref='g1'/></Item>";
        var second = "<Item i:tid='two'><number>2</number><group i:ref='g2'/></Item>";
        var groups = """
                <Group i:tid='g1'><number>1</number></Group>
                <Group i:tid='g2'><number>2</number></Group>
                """;
        for (var items : List.of(first + second, second + first)) {
            var report = validate(plan, List.of("Item", "Group"),
                    primary("p", basket("basket", items + groups)));
            assertEquals(1, report.detectedViolations(), report.examples().toString());
            var diagnostic = report.examples().getFirst();
            assertEquals(ValidationCode.MANDATORY_CONSTRAINT_FALSE, diagnostic.code());
            assertEquals("one", diagnostic.data().tid().orElseThrow().value());
        }
    }

    @Test
    void selfJoinRetainsBothBoundRoles() {
        var plan = plan("""
                CLASS Item = number: 0..10; END Item;
                VIEW Pairs JOIN OF a~Item,b~Item;
                  WHERE a->number < b->number;
                  =
                    MANDATORY CONSTRAINT a->number == 1 AND b->number == 2;
                END Pairs;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid='one'><number>1</number></Item>
                <Item i:tid='two'><number>2</number></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(1, report.coverage().constraints().getFirst().occurrences());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, report.coverage().constraints().getFirst().status());
    }

    @Test
    void distinguishesClosedNullPartnerFromAnOpenExtent() {
        var plan = plan("""
                CLASS LeftSide = amount: 0..10; END LeftSide;
                CLASS RightSide = amount: 0..10; END RightSide;
                VIEW Joined JOIN OF l~LeftSide,r~RightSide (OR NULL); =
                  MANDATORY CONSTRAINT r->amount == UNDEFINED;
                END Joined;
                """);
        var input = primary("p", basket("basket", "<LeftSide i:tid='left'><amount>1</amount></LeftSide>"));
        var complete = validate(plan, List.of("LeftSide", "RightSide"), input);
        assertTrue(complete.examples().isEmpty(), complete.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, complete.coverage().constraints().getFirst().status());
        var open = validate(plan, List.of("LeftSide"), input);
        assertEquals(ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT, open.coverage().constraints().getFirst().status());
    }
    @Test
    void emitsANullPartnerForEachUnmatchedRowInANonemptyExtent() {
        var plan = plan("""
                CLASS Item = amount: 0..10; END Item;
                VIEW Pairs JOIN OF a~Item,b~Item (OR NULL);
                  WHERE a->amount < b->amount;
                  =
                    MANDATORY CONSTRAINT (a->amount == 3) => (b->amount == UNDEFINED);
                END Pairs;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid='one'><amount>1</amount></Item>
                <Item i:tid='two'><amount>2</amount></Item>
                <Item i:tid='three'><amount>3</amount></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(4, report.coverage().constraints().getFirst().occurrences());
    }

    @Test
    void aNullPartnerDoesNotOverrideADefinitivelyFalseSelection() {
        var plan = plan("""
                CLASS Item = amount: 0..10; END Item;
                VIEW Pairs JOIN OF a~Item,b~Item (OR NULL);
                  WHERE a->amount > 1;
                  =
                    MANDATORY CONSTRAINT a->amount > 1;
                END Pairs;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid='one'><amount>1</amount></Item>
                <Item i:tid='two'><amount>2</amount></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
    }

    @Test
    void aNullPartnerDoesNotMaskAFalseConjunct() {
        for (var where : List.of("b->amount > 99 AND a->amount > 5", "a->amount > 5 AND b->amount > 99")) {
            var plan = plan("""
                    CLASS Item = amount: 0..10; END Item;
                    VIEW Pairs JOIN OF a~Item,b~Item (OR NULL);
                      WHERE %s;
                      =
                        MANDATORY CONSTRAINT #false;
                    END Pairs;
                    """.formatted(where));
            var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                    <Item i:tid='one'><amount>1</amount></Item>
                    <Item i:tid='two'><amount>2</amount></Item>
                    """)));
            assertTrue(report.examples().isEmpty(), report.examples().toString());
            assertTrue(report.coverage().constraints().isEmpty());
        }
    }

    @Test
    void failsTheRunWhenCandidateBudgetIsExhausted() {
        var plan = plan("""
                CLASS Item = amount: 0..10; END Item;
                VIEW Pairs JOIN OF a~Item,b~Item; =
                  MANDATORY CONSTRAINT a->amount == b->amount;
                END Pairs;
                """);
        var limits = new DatasetLimits(10, 10, 100, 10_000, 10, 1_000, 12, 10_000);
        var options = new DatasetOptions(ValidationOptions.defaults(), SubjectSelection.fromRoots(plan),
                ExtentDeclarations.open(), limits);
        var token = guru.interlis.execution.CancellationToken.none();
        try (var storage = new InMemoryValidationStorage(1_000, 100_000);
             var run = DatasetValidator.session(plan, options, storage);
             var reader = reader(plan, basket("basket", """
                     <Item i:tid='one'><amount>1</amount></Item>
                     <Item i:tid='two'><amount>2</amount></Item>
                     <Item i:tid='three'><amount>3</amount></Item>
                     """))) {
            run.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), reader, token);
            assertThrows(ValidationLimitException.class, () -> run.finish(token));
            assertThrows(IllegalStateException.class, () -> run.finish(token));
        }
    }
}
