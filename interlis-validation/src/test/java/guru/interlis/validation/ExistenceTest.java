package guru.interlis.validation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ExistenceTest {
    private static final String MODEL = """
            CLASS Target = code: 0.00..20.00; END Target;
            CLASS Other = code: 0.00..20.00; END Other;
            CLASS Unrelated = code: 0.00..20.00; END Unrelated;
            CLASS Subject =
              code: 0.00..20.00;
              EXISTENCE CONSTRAINT code REQUIRED IN Target:code OR Other:code;
            END Subject;
            """;
    private static final String SUBJECT = "<Subject i:tid='sub'><code>1.00</code></Subject>";

    @Test
    void acceptsAReferenceWitnessFromEitherAlternativeRegardlessOfInputOrder() {
        var plan = plan(MODEL);
        var witness = reference("reference", basket("refBasket", "<Other i:tid='ref'><code>1.0</code></Other>"));
        var source = primary("primary", basket("data", SUBJECT));
        for (var report : List.of(validate(plan, List.of(), witness, source),
                validate(plan, List.of(), source, witness))) {
            assertEquals(0, report.detectedViolations());
            assertEquals(1, report.coverage().datasetRules().getFirst().evaluations());
            assertEquals(0, report.coverage().datasetRules().getFirst().undecidedCandidates());
        }
    }

    @Test
    void requiresAllAlternativesClosedForAnAbsenceAndRejectsWrongClassWitnesses() {
        var plan = plan(MODEL);
        var source = primary("primary", basket("data", SUBJECT + """
                <Target i:tid='t'><code>2.00</code></Target>
                <Unrelated i:tid='fake'><code>1.00</code></Unrelated>
                <Subject i:tid='undefined'/>
                """));
        var open = validate(plan, List.of("Target"), source);
        assertEquals(0, open.detectedViolations());
        assertEquals(DatasetRuleCoverage.Outcome.UNDETERMINED, open.coverage().datasetRules().getFirst().outcome());
        assertEquals(1, open.coverage().datasetRules().getFirst().undecidedCandidates());
        var closed = validate(plan, List.of("Target", "Other"), source);
        assertEquals(1, closed.detectedViolations(), closed.examples().toString());
        var missing = closed.examples().getFirst();
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, missing.code());
        assertEquals("sub", missing.data().tid().orElseThrow().value());
        assertEquals(1, missing.data().path().size());
    }

    @Test
    void comparesEffectiveStructureSlotsWithNestedBagMultiplicityAndListOrder() {
        var plan = plan("""
                STRUCTURE Element = value: TEXT*10; END Element;
                STRUCTURE Detail =
                  bag: BAG OF Element;
                  items: LIST OF Element;
                END Detail;
                CLASS Target = detail: Detail; END Target;
                CLASS Subject =
                  detail: Detail;
                  EXISTENCE CONSTRAINT detail REQUIRED IN Target:detail;
                END Subject;
                """);
        var data = """
                <Target i:tid='witness'><detail><Detail>%s%s</Detail></detail></Target>
                <Subject i:tid='match'><detail><Detail>%s%s</Detail></detail></Subject>
                <Subject i:tid='bag'><detail><Detail>%s%s</Detail></detail></Subject>
                <Subject i:tid='list'><detail><Detail>%s%s</Detail></detail></Subject>
                """.formatted(elements("bag", "a", "b", "a"), elements("items", "a", "b"), elements("bag", "b", "a", "a"),
                elements("items", "a", "b"), elements("bag", "a", "b"), elements("items", "a", "b"),
                elements("bag", "a", "a", "b"), elements("items", "b", "a"));
        var report = validate(plan, List.of("Target"), primary("p", basket("data", data)));
        assertEquals(List.of("bag", "list"), report.examples().stream().map(d ->
                d.data().tid().orElseThrow().value()).toList(), report.examples().toString());
        assertTrue(report.examples().stream().allMatch(d -> d.code() == ValidationCode.EXISTENCE_TARGET_MISSING));
    }

    @Test
    void requiresEveryStandardVertexButIgnoresTheArcIntermediatePoint() {
        var plan = plan("""
                DOMAIN Position = COORD 0.00..100.00, 0.00..100.00;
                CLASS Target = point: Position; END Target;
                CLASS Subject =
                  line: POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Position;
                  EXISTENCE CONSTRAINT line REQUIRED IN Target:point;
                END Subject;
                """);
        var start = point("1", "1");
        var middle = point("2", "2");
        var end = point("4", "4");
        var arc = "<g:arc><g:c1>4</g:c1><g:c2>4</g:c2><g:a1>3</g:a1><g:a2>3</g:a2></g:arc>";
        var line = "<Subject i:tid='line'><line><g:polyline>" + start + middle + arc + "</g:polyline></line></Subject>";
        var firstTwo = target("start", start) + target("middle", middle);
        var missing = validate(plan, List.of("Target"), primary("p", basket("data", line + firstTwo)));
        assertEquals(1, missing.detectedViolations(), missing.examples().toString());
        var diagnostic = missing.examples().getFirst();
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, diagnostic.code());
        assertEquals(List.of("1"), diagnostic.arguments());
        assertEquals(DataLocation.GeometryComponent.Kind.END,
                assertInstanceOf(DataLocation.GeometryComponent.class, diagnostic.data().path().getLast()).kind());
        var complete = validate(plan, List.of("Target"), primary("p", basket("data",
                line + firstTwo + target("end", end))));
        assertEquals(0, complete.detectedViolations(), complete.examples().toString());
        assertEquals(1, complete.coverage().datasetRules().getFirst().evaluations());
    }

    @Test
    void doesNotClaimEqualityForPolylineToPolylineExistence() {
        var plan = plan("""
                DOMAIN Position = COORD 0.00..100.00, 0.00..100.00;
                Curve = POLYLINE WITH (STRAIGHTS) VERTEX Position;
                CLASS Target = line: Curve; END Target;
                CLASS Subject =
                  line: Curve;
                  EXISTENCE CONSTRAINT line REQUIRED IN Target:line;
                END Subject;
                """);
        var line = "<line><g:polyline>" + point("1", "1") + point("2", "2") + "</g:polyline></line>";
        var report = validate(plan, List.of("Target"), primary("p", basket("data",
                "<Subject i:tid='s'>" + line + "</Subject><Target i:tid='t'>" + line + "</Target>")));
        assertEquals(0, report.detectedViolations());
        assertEquals(DatasetRuleCoverage.Outcome.UNSUPPORTED_VALUE_SEMANTICS, report.coverage().datasetRules().getFirst().outcome());
    }

    private static String point(String x, String y) {
        return "<g:coord><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2></g:coord>";
    }

    private static String target(String id, String point) {
        return "<Target i:tid='" + id + "'><point>" + point + "</point></Target>";
    }

    private static String elements(String tag, String... values) {
        return List.of(values).stream().map(value -> "<%1$s><Element><value>%2$s</value></Element></%1$s>".formatted(tag, value))
                .collect(java.util.stream.Collectors.joining());
    }
}
