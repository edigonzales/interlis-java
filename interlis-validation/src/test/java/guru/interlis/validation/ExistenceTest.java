package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
import guru.interlis.source.SourceRevision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    void findsSubclassWitnessesInAPoolWithStandaloneAssociations() {
        var plan = plan("""
                CLASS Target = code: 0.00..20.00; END Target;
                CLASS Child EXTENDS Target = END Child;
                CLASS Subject =
                  code: 0.00..20.00;
                  EXISTENCE CONSTRAINT code REQUIRED IN Target:code;
                END Subject;
                ASSOCIATION Link =
                  left -- {0..*} Target;
                  right -- {0..*} Target;
                  code: 0.00..20.00;
                END Link;
                """);
        var link = "<Link><left i:ref='witness'/><right i:ref='witness'/><code>1.00</code></Link>";
        var child = "<Child i:tid='witness'><code>%s</code></Child>";
        for (var code : List.of("1.0", "2.00")) {
            var witness = child.formatted(code);
            for (var pool : List.of(witness + link, link + witness)) {
                var report = validate(plan, List.of("Target"), primary("p", basket("data", SUBJECT + pool)));
                assertEquals(code.equals("1.0") ? 0 : 1, report.detectedViolations(), report.examples().toString());
                if (!report.examples().isEmpty()) {
                    assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, report.examples().getFirst().code());
                }
            }
        }
    }

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

    @Test
    void reportsMissingWitnessWhenTheReferencePathIsUndefined() {
        var plan = plan("""
                STRUCTURE Link = target: REFERENCE TO Peer; END Link;
                CLASS Peer = number: 0.00..20.00; END Peer;
                CLASS Subject =
                  number: 0.00..20.00;
                  link: Link;
                  EXISTENCE CONSTRAINT number REQUIRED IN Subject:link->target->number;
                END Subject;
                """);
        var report = validate(plan, List.of("Subject"), primary("p", basket("data",
                "<Subject i:tid='s'><number>1.00</number></Subject>")));
        assertEquals(1, report.detectedViolations());
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, report.examples().getFirst().code());
        var rule = report.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.VIOLATED, rule.outcome());
        assertEquals(0, rule.undecidedCandidates());
    }

    @Test
    void checksLaterPartsAndInnerRingsOfMultiGeometries() {
        var plan = plan("""
                DOMAIN Position = COORD 0.00..100.00, 0.00..100.00;
                CLASS Target = point: Position; END Target;
                CLASS Subject =
                  lines: MULTIPOLYLINE WITH (STRAIGHTS) VERTEX Position;
                  shape: MULTISURFACE WITH (STRAIGHTS) VERTEX Position WITHOUT OVERLAPS > 0.01;
                  EXISTENCE CONSTRAINT lines REQUIRED IN Target:point;
                  EXISTENCE CONSTRAINT shape REQUIRED IN Target:point;
                END Subject;
                """);
        var a = point("1", "1");
        var b = point("2", "2");
        var c = point("3", "3");
        var missing = point("4", "3");
        var lines = "<g:multipolyline>" + line(a + b) + line(c + missing) + "</g:multipolyline>";
        var firstSurface = "<g:surface><g:exterior>" + line(a + b + c + a) + "</g:exterior></g:surface>";
        var secondSurface = "<g:surface><g:exterior>" + line(a + b + c + a) + "</g:exterior>"
                + "<g:interior>" + line(a + c + missing + a) + "</g:interior></g:surface>";
        var data = "<Subject i:tid='s'><lines>" + lines + "</lines><shape><g:multisurface>"
                + firstSurface + secondSurface + "</g:multisurface></shape></Subject>"
                + target("a", a) + target("b", b) + target("c", c);
        var report = validate(plan, List.of("Target"), primary("p", basket("data", data)));
        var findings = report.examples().stream().filter(d -> d.code() == ValidationCode.EXISTENCE_TARGET_MISSING).toList();
        assertEquals(2, findings.size(), report.examples().toString());
        assertTrue(findings.stream().allMatch(finding -> finding.arguments().equals(List.of("1"))));
        assertEquals(new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.LINE, 2),
                findings.getFirst().data().path().get(1));
        assertEquals(new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.SURFACE, 2),
                findings.getLast().data().path().get(1));
        assertEquals(new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.RING, 2),
                findings.getLast().data().path().get(2));
    }

    @Test
    void checksXtf23ArcEndWithoutDemandingItsIntermediatePosition() {
        var plan = plan("2.3", """
                DOMAIN Position = COORD 0..100, 0..100;
                CLASS Target = point: Position; END Target;
                CLASS Subject =
                  line: POLYLINE WITH (STRAIGHTS, ARCS) VERTEX Position;
                  EXISTENCE CONSTRAINT line REQUIRED IN Target:point;
                END Subject;
                """);
        var data = """
                <Checks.Data BID="data">
                  <Checks.Data.Subject TID="s"><line><POLYLINE>
                    <COORD><C1>1</C1><C2>1</C2></COORD>
                    <ARC><C1>4</C1><C2>4</C2><A1>3</A1><A2>3</A2></ARC>
                  </POLYLINE></line></Checks.Data.Subject>
                  <Checks.Data.Target TID="first"><point><COORD><C1>1</C1><C2>1</C2></COORD></point></Checks.Data.Target>
                  %s
                </Checks.Data>
                """;
        var missing = validate(plan, List.of("Target"), primary("p", data.formatted("")));
        assertEquals(1, missing.detectedViolations(), missing.examples().toString());
        assertEquals(List.of("1"), missing.examples().getFirst().arguments());
        var matching = validate(plan, List.of("Target"), primary("p", data.formatted("""
                <Checks.Data.Target TID="end"><point><COORD><C1>4</C1><C2>4</C2></COORD></point></Checks.Data.Target>
                """)));
        assertEquals(0, matching.detectedViolations(), matching.examples().toString());
        assertEquals(1, matching.coverage().datasetRules().getFirst().evaluations());
    }

    @Test
    void acceptsAClosedTranslatedTargetExtentAndWitnessFromTheOriginalModel() {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    DOMAIN Choices = (first, second);
                    CLASS Catalogue = code: Choices; END Catalogue;
                    CLASS Subject =
                      code: Choices;
                      EXISTENCE CONSTRAINT code REQUIRED IN Catalogue:code;
                    END Subject;
                  END Data;
                END Checks.
                MODEL German (de) AT "https://example.org/checks" VERSION "1" TRANSLATION OF Checks["1"] =
                  TOPIC Daten =
                    DOMAIN Auswahl = (erst, zweit);
                    CLASS Katalog = wert: Auswahl; END Katalog;
                    CLASS Objekt =
                      wert: Auswahl;
                      EXISTENCE CONSTRAINT wert REQUIRED IN Katalog:wert;
                    END Objekt;
                  END Daten;
                END German.
                """, new SourceRevision(1), "Checks", "German");
        var transfer = ValidationPlannerTest.transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(schema, transfer, ValidationProfile.DATASET)).plan();
        var originalInput = new InputId("reference");
        var translatedInput = new InputId("primary");
        var catalogue = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Catalogue")).getFirst();
        var selector = new ExtentSelector(catalogue, Set.of(originalInput, translatedInput), Optional.empty());
        var defaults = DatasetOptions.defaults(plan);
        var options = new DatasetOptions(defaults.localOptions(), defaults.subjects(), new ExtentDeclarations(List.of(
                new ExtentDeclaration(selector, ExtentAspect.OBJECT_MEMBERSHIP, "catalogue inventory"),
                new ExtentDeclaration(selector, ExtentAspect.ATTRIBUTE_VALUES, "catalogue values"))), defaults.limits());
        var token = CancellationToken.none();
        for (var label : List.of("first", "second")) {
            try (var store = new InMemoryValidationStorage(200, 100_000);
                 var run = DatasetValidator.session(plan, options, store);
                 var reference = translatedReader(plan, "Checks", "Catalogue", "ref", label);
                 var source = translatedReader(plan, "German", "Objekt", "source", "erst")) {
                run.readInput(new InputDescriptor(originalInput, InputRole.REFERENCE), reference, token);
                run.readInput(new InputDescriptor(translatedInput, InputRole.PRIMARY), source, token);
                var report = run.finish(token);
                assertEquals(label.equals("first") ? 0 : 1, report.detectedViolations(), report.coverage().toString());
                assertTrue(report.examples().stream().allMatch(d -> d.code() == ValidationCode.EXISTENCE_TARGET_MISSING));
            }
        }
    }

    @Test
    void comparesExplicitlyCompatibleDistinctEnumerationDomainsOnlyForExistence() {
        var plan = plan("""
                DOMAIN CatalogueCode = (yes, no);
                SubjectCode = (yes, no);
                CLASS Target = code: CatalogueCode; END Target;
                CLASS Subject =
                  code: SubjectCode;
                  EXISTENCE CONSTRAINT code REQUIRED IN Target:code;
                END Subject;
                """);
        var witness = "<Target i:tid='w'><code>yes</code></Target>";
        var matched = "<Subject i:tid='yes'><code>yes</code></Subject>";
        var missing = "<Subject i:tid='no'><code>no</code></Subject>";
        var report = validate(plan, List.of("Target"), primary("p", basket("data", witness + matched + missing)));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, report.examples().getFirst().code());
        assertEquals("no", report.examples().getFirst().data().tid().orElseThrow().value());
    }

    @Test
    void treatsMulticoordinatesAsUnorderedSetsOfExactPositions() {
        var plan = plan("""
                DOMAIN Points = MULTICOORD 0.00..100.00, 0.00..100.00;
                CLASS Target = value: Points; END Target;
                CLASS Subject =
                  value: Points;
                  EXISTENCE CONSTRAINT value REQUIRED IN Target:value;
                END Subject;
                """);
        var witness = "<Target i:tid='w'><value><g:multicoord>" + point("1", "1") + point("2", "2")
                + "</g:multicoord></value></Target>";
        var matched = "<Subject i:tid='match'><value><g:multicoord>" + point("2.00", "2") + point("1", "1")
                + "</g:multicoord></value></Subject>";
        var missing = "<Subject i:tid='missing'><value><g:multicoord>" + point("1", "1") + point("3", "3")
                + "</g:multicoord></value></Subject>";
        var report = validate(plan, List.of("Target"), primary("p", basket("data", witness + matched + missing)));
        assertEquals(1, report.detectedViolations(), report.examples().toString());
        assertEquals(ValidationCode.EXISTENCE_TARGET_MISSING, report.examples().getFirst().code());
        assertEquals("missing", report.examples().getFirst().data().tid().orElseThrow().value());
    }

    private static String line(String points) {
        return "<g:polyline>" + points + "</g:polyline>";
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
