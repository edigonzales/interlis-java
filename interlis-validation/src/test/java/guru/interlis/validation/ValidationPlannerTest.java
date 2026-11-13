package guru.interlis.validation;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ResolutionFingerprint;
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
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
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
                SmallAmount EXTENDS Amount = 0..50 CONSTRAINTS Limit: THIS < 45;
              TOPIC T =
                CLASS Base (ABSTRACT) =
                  code : ShortLabel;
                  value : Amount;
                  UNIQUE code;
                END Base;
                CLASS Derived EXTENDS Base =
                  value (EXTENDED) : SmallAmount;
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
        var base = schema.queries().findQualifiedName(QualifiedName.of("Checks", "T", "Base")).getFirst();
        assertEquals(schema.snapshotId(), plan.snapshotId());
        assertEquals(schema.fingerprint(), plan.schemaFingerprint());
        assertNull(plan.object(base));
        var rules = plan.object(derived);
        assertEquals(List.of("code", "value"),
                rules.fields().stream().map(field -> field.slot().attribute().name().value()).toList());
        assertTrue(rules.fields().getFirst().values().getFirst().required());
        var amount = rules.fields().getLast();
        var override = schema.queries().findQualifiedName(QualifiedName.of("Checks", "T", "Derived", "value")).getFirst();
        assertEquals(override, amount.slot().symbol());
        var domainRules = amount.values().getFirst().constraints();
        assertEquals(2, domainRules.size());
        assertEquals(List.of("Minimum", "Limit"), domainRules.stream().map(rule -> rule.name().value()).toList());
        domainRules.forEach(rule -> assertInstanceOf(DomainConstraint.class, rule.definition()));
        assertEquals(1, rules.constraints().size());
        var external = rules.constraints().getFirst();
        assertInstanceOf(MandatoryConstraint.class, external.definition());
        assertEquals(Optional.of(base.symbol()), external.target());
        assertEquals(List.of(UncheckedRuleInfo.Reason.OUT_OF_SCOPE_GLOBAL),
                plan.uncheckedRules().stream().filter(rule -> rule.objectType().equals(derived))
                        .map(UncheckedRuleInfo::reason).toList());
        assertEquals(plan.uncheckedRules(), ready(schema, transfer).uncheckedRules());
        assertThrows(UnsupportedOperationException.class, () -> plan.uncheckedRules().clear());
    }

    @Test
    void rejectsTransferPlanFromAnotherSnapshot() {
        var schema = compile(MODEL);
        var foreign = compile(MODEL, new SourceRevision(2), "Checks");
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

    @Test
    void keepsTranslatedRuleIdentitiesSeparate() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC T = CLASS Item = flag : BOOLEAN; MANDATORY CONSTRAINT flag; END Item; END T;
                END Checks.
                MODEL German (de) AT "https://example.org/checks" VERSION "1" TRANSLATION OF Checks["1"] =
                  TOPIC T = CLASS Item = fahne : BOOLEAN; MANDATORY CONSTRAINT fahne; END Item; END T;
                END German.
                MODEL French (fr) AT "https://example.org/checks" VERSION "1" TRANSLATION OF Checks["1"] =
                  TOPIC T = CLASS Item = drapeau : BOOLEAN; MANDATORY CONSTRAINT drapeau; END Item; END T;
                END French.
                """, new SourceRevision(1), "Checks", "German", "French");
        var plan = ready(schema, transfer(schema));
        var rules = List.of("Checks", "German", "French").stream().map(name -> {
            var type = schema.queries().findQualifiedName(QualifiedName.of(name, "T", "Item")).getFirst();
            var constraints = plan.object(type).constraints();
            assertEquals(1, constraints.size());
            var rule = constraints.getFirst();
            assertEquals(Optional.of(type.symbol()), rule.header().owner());
            return rule.id();
        }).toList();
        assertEquals(3, rules.stream().distinct().count());
    }

    static ValidationPlan ready(CompiledSchema schema, TransferPlan transfer) {
        return assertInstanceOf(ValidationPlanningResult.Ready.class, ValidationPlanner.plan(schema, transfer)).plan();
    }

    static TransferPlan transfer(CompiledSchema schema) {
        return assertInstanceOf(TransferPlanningResult.Ready.class, TransferPlanner.plan(schema)).plan();
    }

    static CompiledSchema compile(String model) {
        return compile(model, new SourceRevision(1), "Checks");
    }

    static CompiledSchema compileWithOriginImport(String model) {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, new SourceRevision(1), model);
        var parsed = SyntaxServices.create().parse(source, ParseOptions.defaults(ParseMode.EDITOR),
                CancellationToken.none());
        var origin = parsed.root().models().getFirst();
        var checks = parsed.root().models().getLast();
        var site = new ImportSite(checks.imports().getFirst().range(), "Checks", "Origin");
        var location = new ModelLocation(id, source.revision(), "Origin",
                Optional.of(origin.modelVersion()), origin.range().startOffset());
        var sources = SourceSet.of(Map.of(id, source),
                List.of(new ImportBinding(site, new ImportResolution.Resolved(location))),
                List.of(), ResolutionFingerprint.none());
        return compile(sources, "Checks");
    }

    static CompiledSchema compile(String model, SourceRevision revision, String... roots) {
        var id = new SourceId("memory:/Checks.ili");
        var source = new SourceText(id, revision, model);
        return compile(SourceSet.of(Map.of(id, source)), roots);
    }

    private static CompiledSchema compile(SourceSet sources, String... roots) {
        List<ModelSelector> selectors = java.util.Arrays.stream(roots)
                .map(name -> (ModelSelector) new ModelSelector.ByName(name, Optional.empty(), Optional.empty())).toList();
        var request = new CompilationRequest(selectors, sources, CompilerOptions.defaults());
        var result = CompilerEngines.create().compile(request, CancellationToken.none());
        return assertInstanceOf(CompilationOutcome.Succeeded.class, result,
                () -> result instanceof CompilationOutcome.Rejected rejected
                        ? rejected.analysis().requestDiagnostics().toString() : result.toString()).schema();
    }
}
