package guru.interlis.validation;

import guru.interlis.execution.CancellationReason;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.QualifiedName;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class FunctionBindingTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
              FUNCTION len(text: TEXT): MANDATORY NUMERIC;
              TOPIC Data =
                CLASS Item =
                  label: TEXT;
                  MANDATORY CONSTRAINT INTERLIS.len(label) == 1 AND Checks.len(label) == 2;
                END Item;
              END Data;
            END Checks.
            """;

    @Test
    void distinguishesShadowFromStandardAndKeepsMissingProviderVisible() {
        var unsupported = report(plan(null));
        assertEquals(1, unsupported.coverage().notChecked().size());
        assertEquals(0, unsupported.detectedViolations());
        var bound = report(plan((arguments, context, cancellation) -> {
            context.consumeWork();
            assertEquals(List.of(new TransferValue.Text("x")), arguments);
            return new TransferValue.NumberValue("2", BigDecimal.valueOf(2));
        }));
        assertTrue(bound.coverage().notChecked().isEmpty());
        assertEquals(0, bound.detectedViolations());
        assertEquals(ValidationCoverage.ConstraintStatus.CHECKED, bound.coverage().constraints().getFirst().status());
    }

    @Test
    void rejectsWrongTypeAndMandatoryUndefinedAndPropagatesTechnicalFailures() {
        assertThrows(FunctionEvaluationException.class,
                () -> report(plan((args, context, cancellation) -> new TransferValue.Text("2"))));
        assertThrows(FunctionEvaluationException.class,
                () -> report(plan((args, context, cancellation) -> TransferValue.Undefined.INSTANCE)));
        var broken = new IllegalStateException("provider broke");
        assertSame(broken, assertThrows(FunctionEvaluationException.class, () -> report(
                plan((args, context, cancellation) -> { throw broken; }))).getCause());
        assertThrows(OperationCancelledException.class, () -> report(plan((args, context, cancellation) -> {
            throw new OperationCancelledException(CancellationReason.USER_REQUESTED);
        })));
    }

    private static ValidationPlan plan(ValidationFunction implementation) {
        var schema = compile(MODEL);
        var ref = schema.queries().findQualifiedName(QualifiedName.of("Checks", "len")).getFirst();
        var signature = (FunctionSymbol) schema.queries().symbol(ref).known().orElseThrow();
        var bindings = implementation == null ? RuntimeBindings.empty()
                : new RuntimeBindings(Map.of(), Map.of(ref, new FunctionBinding(signature, "host.len", "1", implementation)));
        return assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(schema, transfer(schema), ValidationProfile.DATASET, bindings)).plan();
    }

    private static ValidationReport report(ValidationPlan plan) {
        return validate(plan, List.of("Item"), primary("p", basket("basket",
                "<Item i:tid='item'><label>x</label></Item>")));
    }
}
