package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UnionViewTest {
    @Test
    void retainsEqualValuesFromDistinctUnionBranchesAndRunsInheritedRules() {
        var plan = plan("""
                CLASS A = label: TEXT; END A;
                CLASS B = label: TEXT; END B;
                VIEW Merged UNION OF a~A,b~B; =
                  label: TEXT := a->label,b->label;
                  MANDATORY CONSTRAINT label == "same";
                END Merged;
                VIEW Extended EXTENDS Merged =
                  MANDATORY CONSTRAINT INTERLIS.len(label) == 4;
                END Extended;
                """);
        var report = validate(plan, List.of("A", "B"), primary("p", basket("basket", """
                <A i:tid='a'><label>same</label></A>
                <B i:tid='b'><label>same</label></B>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertEquals(3, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream().allMatch(count ->
                count.occurrences() == 2 && count.status() == ValidationCoverage.ConstraintStatus.CHECKED));
    }
}
