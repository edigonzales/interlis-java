package guru.interlis.validation;

import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeParameterTest {
    @Test
    void separatesMissingFromUndefinedAndRejectsWrongBindingFamily() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  PARAMETER enabled: BOOLEAN;
                  TOPIC Data =
                    CLASS Item =
                      MANDATORY CONSTRAINT PARAMETER enabled;
                    END Item;
                  END Data;
                END Checks.
                """);
        var transfer = transfer(schema);
        var parameter = schema.queries().findQualifiedName(QualifiedName.of("Checks", "enabled")).getFirst();
        var input = primary("p", basket("basket", "<Item i:tid='item'/>"));
        for (var entry : Map.of(RuntimeBindings.empty(), ValidationCoverage.ConstraintStatus.MISSING_RUNTIME_CONTEXT,
                new RuntimeBindings(Map.of(parameter, TransferValue.Undefined.INSTANCE), Map.of()),
                ValidationCoverage.ConstraintStatus.UNDEFINED_BY_DATA,
                new RuntimeBindings(Map.of(parameter, new TransferValue.BooleanValue(true)), Map.of()),
                ValidationCoverage.ConstraintStatus.CHECKED).entrySet()) {
            var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                    ValidationPlanner.plan(transfer, ValidationProfile.DATASET, entry.getKey())).plan();
            var report = validate(plan, List.of("Item"), input);
            assertEquals(entry.getValue(), report.coverage().constraints().getFirst().status());
        }
        var invalid = new RuntimeBindings(Map.of(parameter, new TransferValue.Text("true")), Map.of());
        assertInstanceOf(ValidationPlanningResult.Rejected.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET, invalid));
    }
}
