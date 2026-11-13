package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class InspectionViewTest {
    @Test
    void preservesRepeatedStructureOccurrencesAndTheirRealParents() {
        var plan = plan("""
                STRUCTURE Part = label: TEXT; END Part;
                CLASS Item = code: TEXT; parts: BAG OF Part; END Item;
                VIEW Expanded INSPECTION OF part~Item->parts; =
                  owner := PARENT->code;
                  MANDATORY CONSTRAINT owner == "good" AND part->label == "same";
                END Expanded;
                """);
        var repeated = "<parts><Part><label>same</label></Part></parts>";
        var report = validate(plan, DatasetScope.SUPPLIED_DATASET, primary("p", basket("basket",
                "<Item i:tid='a'><code>good</code>" + repeated + repeated + "</Item>"
                        + "<Item i:tid='b'><code>bad</code>" + repeated + "</Item>")));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals("b", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(3, report.coverage().constraints().getFirst().occurrences());
    }
}
