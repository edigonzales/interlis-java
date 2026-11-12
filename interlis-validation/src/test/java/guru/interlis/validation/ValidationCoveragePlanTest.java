package guru.interlis.validation;

import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.UncheckedRuleInfo.Reason.OUT_OF_SCOPE_GLOBAL;
import static guru.interlis.validation.UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.ready;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class ValidationCoveragePlanTest {
    @Test
    void listsEachGlobalRuleAndUnsupportedRuntimeExpressionInDeclarationOrder() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  PARAMETER enabled : BOOLEAN;
                  FUNCTION external(code: TEXT): BOOLEAN;
                  TOPIC T =
                    CLASS Target =
                      code : TEXT*20;
                    END Target;
                    CLASS Item =
                      code : MANDATORY TEXT*20;
                      CONSTRAINT Plausible: >= 90% DEFINED(code);
                      UNIQUE GlobalKey: code;
                      UNIQUE (BASKET) BasketKey: code;
                      EXISTENCE CONSTRAINT KnownCode: code REQUIRED IN Target:code;
                      SET CONSTRAINT CountItems: INTERLIS.objectCount(ALL) >= 1;
                      MANDATORY CONSTRAINT RuntimeFlag: PARAMETER enabled;
                      MANDATORY CONSTRAINT ExternalCall: external(code);
                    END Item;
                  END T;
                END Checks.
                """);
        var plan = ready(schema, transfer(schema));
        var item = schema.queries().findQualifiedName(QualifiedName.of("Checks", "T", "Item")).getFirst();
        var rules = plan.uncheckedRules();
        assertEquals(List.of("Plausible", "GlobalKey", "BasketKey", "KnownCode",
                        "CountItems", "RuntimeFlag", "ExternalCall"),
                rules.stream().map(rule -> (ConstraintSymbol) schema.queries().symbol(rule.constraint())
                                .known().orElseThrow())
                        .map(rule -> rule.name().value()).toList());
        assertEquals(List.of(OUT_OF_SCOPE_GLOBAL, OUT_OF_SCOPE_GLOBAL, OUT_OF_SCOPE_GLOBAL,
                        OUT_OF_SCOPE_GLOBAL, OUT_OF_SCOPE_GLOBAL, UNSUPPORTED_EXPRESSION, UNSUPPORTED_EXPRESSION),
                rules.stream().map(UncheckedRuleInfo::reason).toList());
        assertTrue(rules.stream().allMatch(rule -> rule.objectType().equals(item) && rule.slot().isEmpty()));
        assertTrue(plan.object(item).constraints().isEmpty());
        assertTrue(plan.object(item).fields().getFirst().values().getFirst().required());
    }
}
