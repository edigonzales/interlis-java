package guru.interlis.validation;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SchemaFingerprint;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ValidationPlannerTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
              DOMAIN
                Label = MANDATORY TEXT*30 CONSTRAINTS Length: INTERLIS.len(THIS) > 2;
                ShortLabel EXTENDS Label = TEXT*20;
                Amount = 0..100 CONSTRAINTS Minimum: THIS > 10;
              TOPIC T =
                CLASS Base =
                  code : ShortLabel;
                  value : Amount;
                  UNIQUE code;
                END Base;
                CLASS Derived EXTENDS Base =
                END Derived;
                CONSTRAINTS OF Base =
                  MANDATORY CONSTRAINT DEFINED(code);
                END;
              END T;
            END Checks.
            """;

    @Test
    void projectsInheritedExternalAndDomainRulesAndReportsUncheckedApplications() {
        var schema = compile(MODEL);
        var transfer = transfer(schema);
        var plan = ready(schema, transfer);
        var derived = schema.queries().findQualifiedName(QualifiedName.of("Checks", "T", "Derived")).getFirst();
        var rules = plan.object(derived);
        assertEquals(List.of("code", "value"),
                rules.fields().stream().map(field -> field.slot().attribute().name().value()).toList());
        assertTrue(rules.fields().getFirst().values().getFirst().required());
        assertInstanceOf(DomainConstraint.class, rules.fields().getLast().values().getFirst().constraints().getFirst().definition());
        assertEquals(1, rules.constraints().size());
        assertInstanceOf(MandatoryConstraint.class, rules.constraints().getFirst().definition());
        assertEquals(List.of(UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION, UncheckedRuleInfo.Reason.OUT_OF_SCOPE_GLOBAL),
                plan.uncheckedRules().stream().filter(rule -> rule.objectType().equals(derived))
                        .map(UncheckedRuleInfo::reason).toList());
        assertEquals(plan.uncheckedRules(), ready(schema, transfer).uncheckedRules());
        assertThrows(UnsupportedOperationException.class, () -> plan.uncheckedRules().clear());
    }

    @Test
    void rejectsTransferPlanFromAnotherSnapshot() {
        var schema = compile(MODEL);
        var foreign = compile(MODEL.replace("VERSION \"1\"", "VERSION \"2\""));
        var rejected = assertInstanceOf(ValidationPlanningResult.Rejected.class,
                ValidationPlanner.plan(schema, transfer(foreign)));
        assertEquals(PlanningProblem.Code.SNAPSHOT_MISMATCH, rejected.problems().getFirst().code());
    }

    @Test
    void rejectsDifferentFingerprintEvenWithMatchingSnapshot() {
        var schema = compile(MODEL);
        var altered = (CompiledSchema) Proxy.newProxyInstance(CompiledSchema.class.getClassLoader(),
                new Class<?>[]{CompiledSchema.class}, (proxy, method, args) ->
                        method.getName().equals("fingerprint")
                                ? new SchemaFingerprint("test", "another-schema")
                                : method.invoke(schema, args));
        var rejected = assertInstanceOf(ValidationPlanningResult.Rejected.class,
                ValidationPlanner.plan(altered, transfer(schema)));
        assertEquals(PlanningProblem.Code.SCHEMA_FINGERPRINT_MISMATCH, rejected.problems().getFirst().code());
    }

    private static ValidationPlan ready(CompiledSchema schema, TransferPlan transfer) {
        return assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(schema, transfer)).plan();
    }

    private static TransferPlan transfer(CompiledSchema schema) {
        return assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
    }

    private static CompiledSchema compile(String model) {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, new SourceRevision(1), model);
        var request = new CompilationRequest(List.of(new ModelSelector.ByName("Checks", Optional.empty(), Optional.empty())),
                SourceSet.of(Map.of(id, source)), CompilerOptions.defaults());
        var result = CompilerEngines.create().compile(request, CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().requestDiagnostics().toString() : result.toString()).schema();
    }
}
