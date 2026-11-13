package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectionViewTest {
    @Test
    void keepsUnavailableDerivedValuesAcrossProjectionBoundaries() {
        var plan = plan("""
                FUNCTION label(value: TEXT): TEXT;
                CLASS Item = name: TEXT; END Item;
                VIEW SourceView PROJECTION OF item~Item; =
                  computed := label(item->name);
                END SourceView;
                VIEW Result PROJECTION OF renamed~SourceView; =
                  MANDATORY CONSTRAINT DEFINED(renamed->computed);
                END Result;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket",
                "<Item i:tid='item'><name>value</name></Item>")));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        assertEquals(1, report.coverage().constraints().getFirst().occurrences());
        assertEquals(ValidationCoverage.ConstraintStatus.UNSUPPORTED_VALUE,
                report.coverage().constraints().getFirst().status());
    }

    @Test
    void validatesSelectedVirtualRowsAndReportsRealOrigin() {
        var plan = plan("""
                CLASS Item = amount: 0..10; END Item;
                VIEW Selected PROJECTION OF item~Item;
                  WHERE item->amount > 0;
                  =
                    doubled := item->amount + item->amount;
                    MANDATORY CONSTRAINT doubled > 2;
                END Selected;
                """);
        var report = validate(plan, List.of("Item"), primary("p", basket("basket", """
                <Item i:tid='filtered'><amount>0</amount></Item>
                <Item i:tid='wrong'><amount>1</amount></Item>
                <Item i:tid='right'><amount>2</amount></Item>
                """)));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("wrong", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
    }
    @Test
    void retainsPlainBaseRowsAndBindsExtensionAliasesOnlyForTheirSubtype() {
        var plan = plan("""
                CLASS Base = amount: 0..10; END Base;
                CLASS Child EXTENDS Base = extra: TEXT; END Child;
                VIEW Selected PROJECTION OF b~Base;
                  BASE b EXTENDED BY child~Child
                  =
                    MANDATORY CONSTRAINT b->amount < 5;
                    MANDATORY CONSTRAINT (b->amount >= 5) => DEFINED(child->extra);
                END Selected;
                """);
        var report = validate(plan, List.of("Base"), primary("p", basket("basket", """
                <Base i:tid='plain'><amount>3</amount></Base>
                <Child i:tid='extended'><amount>7</amount><extra>available</extra></Child>
                """)));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("extended", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(2, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream().allMatch(count -> count.occurrences() == 2));
    }
}
