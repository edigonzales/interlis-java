package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class JoinViewTest {
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
