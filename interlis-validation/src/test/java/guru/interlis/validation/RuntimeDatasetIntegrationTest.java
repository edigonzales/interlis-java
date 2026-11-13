package guru.interlis.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeDatasetIntegrationTest {
    @ParameterizedTest
    @ValueSource(strings = {"2.3", "2.4"})
    void combinesTwoPrimaryInputsAReferenceWitnessAndAVirtualView(String version) {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS %s;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Lookup = code: TEXT; END Lookup;
                    CLASS Base (ABSTRACT) =
                      code: TEXT;
                      EXISTENCE CONSTRAINT code REQUIRED IN Lookup:code;
                    END Base;
                    CLASS Item EXTENDS Base = END Item;
                    VIEW Projection PROJECTION OF item~Item;
                      WHERE DEFINED(item->code);
                      =
                        cleaned := INTERLIS.trim(item->code);
                        MANDATORY CONSTRAINT INTERLIS.len(cleaned) == 4;
                    END Projection;
                  END Data;
                END Checks.
                """.formatted(version));
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(schema,
                ValidationPlannerTest.transfer(schema), ValidationProfile.DATASET)).plan();
        var good = primary("first", xml(version, "first-basket", "Item", "ok", "good"));
        var bad = primary("second", xml(version, "second-basket", "Item", "bad", "wrong"));
        var witness = reference("reference", xml(version, "reference-basket", "Lookup", "witness", "good"));
        var report = validate(plan, List.of("Lookup", "Item"), good, bad, witness);
        assertEquals(List.of(ValidationCode.MANDATORY_CONSTRAINT_FALSE, ValidationCode.EXISTENCE_TARGET_MISSING),
                report.examples().stream().map(ValidationDiagnostic::code).toList());
        assertTrue(report.examples().stream().allMatch(diagnostic ->
                diagnostic.data().tid().orElseThrow().value().equals("bad") && diagnostic.rule().range().isPresent()));
        assertEquals(2, report.coverage().constraints().getFirst().occurrences());
        assertEquals(2, report.inspectedObjects());
    }

    private static String xml(String version, String id, String type, String tid, String value) {
        return version.equals("2.3")
                ? "<Checks.Data BID='%s'><Checks.Data.%s TID='%s'><code>%s</code></Checks.Data.%s></Checks.Data>"
                        .formatted(id, type, tid, value, type)
                : basket(id, "<%s i:tid='%s'><code>%s</code></%s>".formatted(type, tid, value, type));
    }
}
