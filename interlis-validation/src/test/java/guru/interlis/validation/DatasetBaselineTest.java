package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static guru.interlis.validation.ValidationCode.MANDATORY_CONSTRAINT_FALSE;
import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetBaselineTest {
    @Test
    void validatesEmbeddedAssociationPayload() {
        var model = """
                TOPIC Data =
                  CLASS Parent = END Parent;
                  CLASS Item = END Item;
                  ASSOCIATION Link =
                    parent -- {0..1} Parent;
                    items -- {0..*} Item;
                    note : TEXT*20;
                    MANDATORY CONSTRAINT NeedsNote: DEFINED(note);
                  END Link;
                END Data;
                """;
        var embedded = validate(model, """
                <Data i:bid="b1">
                  <Parent i:tid="p1"/>
                  <Item i:tid="i1"><parent i:ref="p1"><Link/></parent></Item>
                </Data>
                """);
        var standalone = validate(model.replace("parent -- {0..1}", "parent -- {0..*}"), """
                <Data i:bid="b1">
                  <Parent i:tid="p1"/>
                  <Item i:tid="i1"/>
                  <Link><parent i:ref="p1"/><items i:ref="i1"/></Link>
                </Data>
                """);
        for (var report : List.of(embedded, standalone)) {
            assertEquals(List.of(MANDATORY_CONSTRAINT_FALSE),
                    report.examples().stream().map(ValidationDiagnostic::code).toList());
        }
        assertEquals(1, embedded.examples().getFirst().data().path().size());
        assertTrue(standalone.examples().getFirst().data().path().isEmpty());
    }

    @Test
    void appliesExternalConstraintOnlyInItsTopicContext() {
        var report = validate("""
                TOPIC Base =
                  CLASS Item = value : 0..10; END Item;
                END Base;
                TOPIC Extended EXTENDS Base =
                  CONSTRAINTS OF Item = MANDATORY CONSTRAINT Positive: value > 0; END;
                END Extended;
                """, """
                <Base i:bid="base"><Item i:tid="i1"><value>0</value></Item></Base>
                <Extended i:bid="extended"><Item i:tid="i2"><value>0</value></Item></Extended>
                """);
        assertEquals(List.of("extended"), report.examples().stream()
                .filter(d -> d.code() == MANDATORY_CONSTRAINT_FALSE).map(d -> d.data().basket().value()).toList());
    }

    @Test
    void distinguishesPossibleGenericRulesFromObservedApplications() {
        var report = validate("""
                FUNCTION external(p: COORD NUMERIC, NUMERIC): BOOLEAN;
                DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                       A = COORD 0.00..100.00, 0.00..100.00 CONSTRAINTS External: external(THIS);
                       B = COORD 0.00..100.00, 0.00..100.00 CONSTRAINTS Defined: DEFINED(THIS);
                CONTEXT Default = G = A OR B;
                TOPIC Data =
                  DEFERRED GENERICS G;
                  CLASS Item = point : G; END Item;
                END Data;
                """, """
                <Data i:bid="a" i:domains="Checks.G=Checks.A">
                  <Item i:tid="i1"><point><g:coord><g:c1>10.00</g:c1><g:c2>20.00</g:c2></g:coord></point></Item>
                </Data>
                <Data i:bid="b" i:domains="Checks.G=Checks.B">
                  <Item i:tid="i2"><point><g:coord><g:c1>10.00</g:c1><g:c2>20.00</g:c2></g:coord></point></Item>
                </Data>
                """);
        assertEquals(0, report.detectedViolations());
        assertEquals(List.of(UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION),
                report.coverage().notChecked().stream().map(UncheckedRuleInfo::reason).toList());
        assertEquals(1, report.coverage().constraints().size());
        assertEquals(1, report.coverage().constraints().getFirst().occurrences());
        assertTrue(report.coverage().missingRuntimeContext().isEmpty());
    }

    @Test
    void reportsExpressionPlanBudgetSeparatelyFromUnsupportedSyntax() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Item =
                      MANDATORY CONSTRAINT TooDeep: %s;
                    END Item;
                  END Data;
                END Checks.
                """.formatted(String.join(" AND ", java.util.Collections.nCopies(160, "#true"))));
        var rejected = assertInstanceOf(ValidationPlanningResult.Rejected.class,
                ValidationPlanner.plan(schema, transfer(schema)));
        assertEquals(PlanningProblem.Code.EXPRESSION_LIMIT_EXCEEDED, rejected.problems().getFirst().code());
    }

    private static ValidationReport validate(String declarations, String data) {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                """ + declarations + "END Checks.");
        var transfer = transfer(schema);
        var xml = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                            xmlns="http://www.interlis.ch/xtf/2.4/Checks" xmlns:g="http://www.interlis.ch/geometry/1.0">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection>%s</i:datasection>
                </i:transfer>
                """.formatted(data);
        try (var reader = new Xtf24Reader(transfer, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            return LocalValidator.validate(ready(schema, transfer), reader, ValidationOptions.defaults(), CancellationToken.none());
        }
    }
}
