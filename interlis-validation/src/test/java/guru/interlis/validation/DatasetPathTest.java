package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetPathTest {
    @Test
    void followsTwoCrossBasketReferenceHopsIncludingACycle() {
        var plan = plan("""
                CLASS Node =
                  amount: 0..100;
                  pointer: Pointer;
                  MANDATORY CONSTRAINT pointer->link->pointer->link->amount == amount;
                END Node;
                STRUCTURE Pointer =
                  link: REFERENCE TO (EXTERNAL) Node;
                END Pointer;
                """);
        var first = basket("first", """
                <Node i:tid="a"><amount>5</amount><pointer><Pointer><link i:ref="b"/></Pointer></pointer></Node>
                """);
        var second = basket("second", """
                <Node i:tid="b"><amount>7</amount><pointer><Pointer><link i:ref="a"/></Pointer></pointer></Node>
                """);
        var report = validate(plan, List.of("Node"), primary("p", first + second));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(1, report.coverage().constraints().size());
        var checked = report.coverage().constraints().getFirst();
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, checked.status());
        assertEquals(2, checked.occurrences());
    }
    @Test
    void followsForwardAndInverseAssociationEndsWithoutSelectingAnArbitraryTarget() {
        var plan = plan("""
                CLASS Parent = END Parent;
                CLASS Item = END Item;
                ASSOCIATION Link =
                  parent -- {1} Parent;
                  items -- {0..*} Item;
                END Link;
                CONSTRAINTS OF Parent =
                  MANDATORY CONSTRAINT INTERLIS.objectCount(items) == 2;
                END;
                CONSTRAINTS OF Item =
                  MANDATORY CONSTRAINT DEFINED(parent);
                END;
                """);
        var report = validate(plan, List.of("Parent", "Item"), primary("p", basket("basket", """
                <Parent i:tid="p1"/>
                <Item i:tid="i1"><parent i:ref="p1"/></Item>
                <Item i:tid="i2"><parent i:ref="p1"/></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(2, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream()
                .allMatch(rule -> rule.status() == ValidationCoverage.ConstraintStatus.CHECKED));
    }
}
