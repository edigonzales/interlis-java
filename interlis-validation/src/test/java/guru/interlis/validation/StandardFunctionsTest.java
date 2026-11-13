package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class StandardFunctionsTest {
    @Test
    void invokesTextCollectionClassAndEnumFunctionsFromBoundCalls() {
        var plan = plan("""
                STRUCTURE Entry = code: TEXT; END Entry;
                CLASS Base (ABSTRACT) = END Base;
                CLASS Item EXTENDS Base =
                  single: TEXT;
                  multi: MTEXT;
                  phase: (early(first,second),late) ORDERED;
                  entries: BAG OF Entry;
                  MANDATORY CONSTRAINT INTERLIS.len(INTERLIS.trim(single)) == 1;
                  MANDATORY CONSTRAINT INTERLIS.lenM(INTERLIS.trimM(multi)) == 2;
                  MANDATORY CONSTRAINT INTERLIS.elementCount(entries) == 2;
                  MANDATORY CONSTRAINT INTERLIS.isOfClass(THIS, >Base);
                  MANDATORY CONSTRAINT INTERLIS.isSubClass(INTERLIS.myClass(THIS), >Base);
                  MANDATORY CONSTRAINT INTERLIS.isEnumSubVal(phase, #early);
                  MANDATORY CONSTRAINT INTERLIS.inEnumRange(phase, #early, #early);
                  SET CONSTRAINT INTERLIS.objectCount(ALL) == 1;
                END Item;
                """);
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket", """
                <Item i:tid="item">
                  <single> e&#x301; </single><multi> äb </multi><phase>early.second</phase>
                  <entries><Entry><code>same</code></Entry></entries><entries><Entry><code>same</code></Entry></entries>
                </Item>
                """)));
        assertTrue(report.examples().isEmpty(), report.examples().toString());
        assertTrue(report.coverage().notChecked().isEmpty());
        assertEquals(7, report.coverage().constraints().size());
        assertTrue(report.coverage().constraints().stream()
                .allMatch(count -> count.status() == ValidationCoverage.ConstraintStatus.CHECKED));
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, report.coverage().datasetRules().getFirst().outcome());
    }
    @Test
    void convertsNumericalUnitChainsUsingBoundSourceAndTarget() {
        var plan = plan("""
                UNIT kilometre [km] = 1000 [INTERLIS.m];
                     doubleKilometre [dkm] = 2 [km];
                CLASS Item =
                  distance: 0..10 [dkm];
                  metres: 0..20000 [INTERLIS.m];
                  MANDATORY CONSTRAINT INTERLIS.convertUnit(distance) == metres;
                  MANDATORY CONSTRAINT INTERLIS.convertUnit(metres) == distance;
                END Item;
                """);
        var good = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket",
                "<Item i:tid='item'><distance>2</distance><metres>4000</metres></Item>")));
        assertTrue(good.examples().isEmpty(), good.examples().toString());
        assertTrue(good.coverage().notChecked().isEmpty());
        assertEquals(2, good.coverage().constraints().size());
        var bad = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket",
                "<Item i:tid='item'><distance>2</distance><metres>3999</metres></Item>")));
        assertEquals(2, bad.detectedViolations());
    }
}
