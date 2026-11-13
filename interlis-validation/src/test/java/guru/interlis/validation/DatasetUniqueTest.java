package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceRevision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static guru.interlis.validation.DatasetRuleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetUniqueTest {
    @Test
    void detectsDuplicateTerminalReferencesWhenTheTargetIsMissing() {
        var plan = plan("""
                CLASS Peer = END Peer;
                STRUCTURE Link = peer: REFERENCE TO Peer; END Link;
                CLASS Item =
                  link: Link;
                  UNIQUE link->peer;
                END Item;
                """);
        var first = "<Item i:tid='first'><link><Link><peer i:ref='missing'/></Link></link></Item>";
        var second = "<Item i:tid='second'><link><Link><peer i:ref='%s'/></Link></link></Item>";
        for (var target : List.of("missing", "other")) {
            var report = validate(plan, List.of("Item", "Peer"),
                    primary("p", basket("data", first + second.formatted(target))));
            assertEquals(target.equals("missing") ? 1 : 0, report.examples().stream()
                    .filter(diagnostic -> diagnostic.code() == ValidationCode.DATASET_UNIQUE_DUPLICATE).count(),
                    report.examples().toString());
        }
    }

    @Test
    void groupsInheritedGlobalKeysAcrossSiblingsBasketsAndInputs() {
        var plan = plan("""
                CLASS Base (ABSTRACT) =
                  code: TEXT*20;
                  amount: 0.00..20.00;
                  UNIQUE code, amount;
                  UNIQUE (BASKET) code, amount;
                END Base;
                CLASS Left EXTENDS Base = END Left;
                CLASS Right EXTENDS Base = END Right;
                """);
        var first = "<Left i:tid='first'><code>ab</code><amount>1.0</amount></Left>";
        var second = "<Right i:tid='second'><code>ab</code><amount>1.00</amount></Right>";
        var third = "<Left i:tid='third'><code>ab</code><amount>1.000</amount></Left>";
        var report = validate(plan, List.of(),
                primary("main", basket("same", first) + basket("later", second)),
                primary("other", basket("same", third)));
        assertEquals(1, report.detectedViolations());
        var duplicate = report.examples().getFirst();
        assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, duplicate.code());
        assertEquals(List.of("main", "later", "second", "3"), duplicate.arguments());
        assertEquals("first", duplicate.data().tid().orElseThrow().value());
        assertEquals(1, report.coverage().datasetRules().stream().filter(
                rule -> rule.outcome() == DatasetRuleCoverage.Outcome.VIOLATED).count());
        assertEquals(3, report.coverage().datasetRules().stream().filter(
                rule -> rule.scopeKind() == DatasetRuleCoverage.ScopeKind.BASKET).findFirst().orElseThrow().scopes());

        var sameBasket = validate(plan, List.of(), primary("main", basket("together", first + second)));
        assertEquals(2, sameBasket.detectedViolations());
    }

    @Test
    void excludesFalseAndUndefinedWhereAndUndefinedKeysButRetainsExactText() {
        var plan = plan("""
                CLASS Item =
                  active: BOOLEAN;
                  code: TEXT*20;
                  tail: TEXT*20;
                  UNIQUE WHERE active: code, tail;
                END Item;
                """);
        var rows = """
                <Item i:tid='a'><active>true</active><code>é</code><tail>x</tail></Item>
                <Item i:tid='b'><active>true</active><code>é</code><tail>x</tail></Item>
                <Item i:tid='c'><active>false</active><code>é</code><tail>x</tail></Item>
                <Item i:tid='d'><code>é</code><tail>x</tail></Item>
                <Item i:tid='e'><active>true</active><code>é</code></Item>
                <Item i:tid='f'><active>true</active><code>é</code></Item>
                """;
        var report = validate(plan, List.of(), primary("p", basket("basket", rows)));
        assertEquals(0, report.detectedViolations(), report.examples().toString());
        var scope = report.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.NO_VIOLATION_IN_AVAILABLE_SCOPE, scope.outcome());
        assertEquals(6, scope.evaluations());
        assertEquals(0, scope.undecidedCandidates());
    }

    @Test
    void referenceInputsSupplyWitnessesWithoutPromotingReferenceOnlyConflicts() {
        var plan = plan("CLASS Item = code: TEXT*20; UNIQUE code; END Item;");
        var report = validate(plan, List.of(),
                reference("ref", basket("refBasket", """
                        <Item i:tid='ref1'><code>x</code></Item>
                        <Item i:tid='ref2'><code>x</code></Item>
                        <Item i:tid='ref3'><code>y</code></Item>
                        <Item i:tid='ref4'><code>y</code></Item>
                        """)),
                primary("primary", basket("primaryBasket", "<Item i:tid='subject'><code>x</code></Item>")));
        assertEquals(1, report.detectedViolations());
        assertEquals("subject", report.examples().getFirst().data().tid().orElseThrow().value());
        assertEquals(List.of("ref", "refBasket", "ref1", "3"), report.examples().getFirst().arguments());
    }

    @Test
    void standardFunctionWhereParticipatesInDuplicateDetection() {
        var plan = plan("""
                CLASS Item =
                  active: BOOLEAN;
                  code: TEXT*20;
                  UNIQUE WHERE INTERLIS.isOfClass(THIS, >Item): code;
                END Item;
                """);
        var report = validate(plan, List.of(), primary("p", basket("data",
                "<Item i:tid='i1'><code>x</code></Item><Item i:tid='i2'><code>x</code></Item>")));
        assertEquals(1, report.detectedViolations());
        var coverage = report.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.VIOLATED, coverage.outcome());
        assertEquals(0, coverage.undecidedCandidates());
    }
    @Test
    void evaluatesAnEmptyBasketWithAReferenceWhere() {
        var plan = plan("""
                CLASS Peer = active: BOOLEAN; END Peer;
                STRUCTURE Link = peer: REFERENCE TO Peer; END Link;
                CLASS Item =
                  link: Link;
                  code: TEXT*20;
                  UNIQUE (BASKET) WHERE link->peer->active: code;
                END Item;
                """);
        var report = validate(plan, List.of(), primary("p", basket("empty", "")));
        assertEquals(0, report.detectedViolations());
        var coverage = report.coverage().datasetRules().getFirst();
        assertEquals(DatasetRuleCoverage.Outcome.SATISFIED, coverage.outcome());
        assertEquals(1, coverage.scopes());
        assertEquals(0, coverage.candidates());
        assertEquals(0, coverage.evaluations());
    }

    @Test
    void translatesEnumeratedKeyLabelsToTheOriginalDomain() {
        var schema = ValidationPlannerTest.compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    DOMAIN Choices = (first, second);
                    CLASS Item = choice: Choices; UNIQUE choice; END Item;
                  END Data;
                END Checks.
                MODEL German (de) AT "https://example.org/checks" VERSION "1" TRANSLATION OF Checks["1"] =
                  TOPIC Daten =
                    DOMAIN Auswahl = (erst, zweit);
                    CLASS Objekt = wahl: Auswahl; UNIQUE wahl; END Objekt;
                  END Daten;
                END German.
                """, new SourceRevision(1), "Checks", "German");
        var transfer = ValidationPlannerTest.transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
        var token = CancellationToken.none();
        try (var store = new InMemoryValidationStorage(100, 100_000);
             var run = DatasetValidator.session(plan, DatasetOptions.defaults(plan), store);
             var original = translatedReader(plan, "Checks", "Item", "original", "first");
             var translated = translatedReader(plan, "German", "Objekt", "translated", "erst")) {
            run.readInput(new InputDescriptor(new InputId("one"), InputRole.PRIMARY), original, token);
            run.readInput(new InputDescriptor(new InputId("two"), InputRole.PRIMARY), translated, token);
            var result = run.finish(token);
            assertEquals(1, result.detectedViolations(), result.coverage().toString());
            assertEquals(ValidationCode.DATASET_UNIQUE_DUPLICATE, result.examples().getFirst().code());
            assertEquals(List.of("two", "basket", "translated", "2"), result.examples().getFirst().arguments());
        }
    }

}
