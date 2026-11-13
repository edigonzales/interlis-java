package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RelationCardinalityTest {
    @Test
    void checksMinimumForAnObjectWithNoLinksInACompleteBasket() {
        var complete = validate("COMPLETE", """
                <Parent i:tid="p1"/>
                <Item i:tid="i1"/>
                """);
        assertEquals(List.of(ValidationCode.CARDINALITY_VIOLATION), codes(complete));
        assertEquals(List.of("0", "1", "1"), complete.examples().getFirst().arguments());
        assertEquals("i1", complete.examples().getFirst().data().tid().orElseThrow().value());

        var incomplete = validate("INCOMPLETE", """
                <Parent i:tid="p1"/>
                <Item i:tid="i1"/>
                """);
        assertTrue(incomplete.examples().isEmpty());
        assertTrue(incomplete.coverage().missingRuntimeContext().stream()
                .anyMatch(count -> count.family() == ValidationCoverage.RuleFamily.CARDINALITY));

        var linked = validate("COMPLETE", """
                <Parent i:tid="p1"/>
                <Item i:tid="i1"><parent i:ref="p1"/></Item>
                """);
        assertTrue(linked.examples().isEmpty());
    }

    private static ValidationReport validate(String consistency, String data) {
        return validateModel("""
                CLASS Parent = END Parent;
                CLASS Item = END Item;
                ASSOCIATION Link =
                  parent -- {1} Parent;
                  items -- {0..*} Item;
                END Link;
                """, consistency, data);
    }

    @Test
    void countsDistinctThirdEndsForEachPairAndChecksPairsWithoutLinks() {
        var report = validateModel("""
                CLASS A = END A;
                CLASS B = END B;
                CLASS C = END C;
                ASSOCIATION Triple =
                  a -- {0..*} A;
                  b -- {0..*} B;
                  c -- {1} C;
                END Triple;
                """, "COMPLETE", """
                <A i:tid="a1"/><A i:tid="a2"/><B i:tid="b1"/><B i:tid="b2"/>
                <C i:tid="c1"/><C i:tid="c2"/>
                <Triple><a i:ref="a1"/><b i:ref="b1"/><c i:ref="c1"/></Triple>
                <Triple><a i:ref="a1"/><b i:ref="b1"/><c i:ref="c2"/></Triple>
                <Triple><a i:ref="a2"/><b i:ref="b1"/><c i:ref="c1"/></Triple>
                <Triple><a i:ref="a2"/><b i:ref="b2"/><c i:ref="c2"/></Triple>
                """);
        assertEquals(List.of(List.of("0", "1", "1"), List.of("2", "1", "1")),
                report.examples().stream().filter(d -> d.code() == ValidationCode.CARDINALITY_VIOLATION)
                        .map(ValidationDiagnostic::arguments).sorted(java.util.Comparator.comparing(List::getFirst)).toList());
        assertEquals(2, report.examples().size(), report.examples().toString());
    }

    @Test
    void checksLinkMultiplicitySeparatelyFromDistinctRoleTargets() {
        var report = validateModel("""
                CLASS A = END A;
                CLASS B = END B;
                CLASS C = END C;
                ASSOCIATION Triple =
                  a -- {0..*} A;
                  b -- {0..*} B;
                  c -- {1} C;
                END Triple;
                """, "INCOMPLETE", """
                <A i:tid="a1"/><B i:tid="b1"/><C i:tid="c1"/>
                <Triple><a i:ref="a1"/><b i:ref="b1"/><c i:ref="c1"/></Triple>
                <Triple><a i:ref="a1"/><b i:ref="b1"/><c i:ref="c1"/></Triple>
                """);
        assertEquals(List.of(ValidationCode.CARDINALITY_VIOLATION), codes(report));
        assertEquals(List.of("2", "0", "1"), report.examples().getFirst().arguments());
        assertTrue(report.examples().getFirst().data().path().isEmpty());
    }

    @Test
    void observedOverflowIsConclusiveEvenInAnIncompleteBasket() {
        var contents = new StringBuilder("<A i:tid='a1'/><B i:tid='b1'/>");
        for (int i = 0; i < 100; i++) {
            contents.append("<C i:tid='c").append(i).append("'/>");
        }
        for (int i = 0; i < 100; i++) {
            contents.append("<Triple><a i:ref='a1'/><b i:ref='b1'/><c i:ref='c")
                    .append(i).append("'/></Triple>");
        }
        var report = validateModel("""
                CLASS A = END A;
                CLASS B = END B;
                CLASS C = END C;
                ASSOCIATION Triple =
                  a -- {0..*} A;
                  b -- {0..*} B;
                  c -- {0..99} C;
                END Triple;
                """, "INCOMPLETE", contents.toString());
        assertEquals(List.of(ValidationCode.CARDINALITY_VIOLATION), codes(report));
        assertEquals(List.of("100", "0", "99"), report.examples().getFirst().arguments());
    }

    @Test
    void stopsOnRelationshipWorkExhaustionWithoutAReport() {
        var failure = assertThrows(ValidationLimitException.class, () -> validateModel("""
                CLASS A = END A;
                CLASS B = END B;
                ASSOCIATION Link =
                  a -- {1} A;
                  b -- {0..*} B;
                END Link;
                """, "COMPLETE", "<A i:tid='a1'/><B i:tid='b1'/>",
                new DatasetLimits(1, 1, 2, 10_000, 1)));
        assertEquals("Dataset relationship work limit exceeded", failure.getMessage());
    }

    static ValidationReport validateModel(String declarations, String consistency, String data) {
        return validateModel(declarations, consistency, data, DatasetLimits.defaults());
    }

    private static ValidationReport validateModel(String declarations, String consistency, String data,
                                                  DatasetLimits limits) {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    %s
                  END Data;
                END Checks.
                """.formatted(declarations));
        var transfer = transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
        var xml = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                            xmlns="http://www.interlis.ch/xtf/2.4/Checks">
                  <i:headersection><i:models><i:model>Checks</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><Data i:bid="basket" i:consistency="%s">%s</Data></i:datasection>
                </i:transfer>
                """.formatted(consistency, data);
        var defaults = DatasetOptions.defaults();
        var options = new DatasetOptions(defaults.localOptions(), limits, defaults.scope());
        try (var store = new InMemoryValidationStorage(10_000, 4_000_000);
             var run = DatasetValidator.session(plan, options, store);
             var reader = new Xtf24Reader(transfer, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))) {
            var token = CancellationToken.none();
            run.readInput(new InputDescriptor(new InputId("primary"), InputRole.PRIMARY), reader, token);
            return run.finish(token);
        }
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }
}
