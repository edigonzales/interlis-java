package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DerivedValueTest {
    @Test
    void readsLastDefinedOverrideAndDoesNotRequireTransientTransferValue() {
        var plan = plan("""
                CLASS Base (ABSTRACT) =
                  first: 0..10;
                  second: 0..10;
                  chosen (TRANSIENT): 0..10 := first;
                END Base;
                CLASS Item EXTENDS Base =
                  chosen (EXTENDED, TRANSIENT): 0..10 := second;
                  expected: 0..10;
                  MANDATORY CONSTRAINT chosen == expected;
                END Item;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid="a"><first>1</first><second>2</second><expected>2</expected></Item>
                <Item i:tid="b"><first>1</first><expected>1</expected></Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, report.coverage().constraints().getFirst().status());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
    }
}
