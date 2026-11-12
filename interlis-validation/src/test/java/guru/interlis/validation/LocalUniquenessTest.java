package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalUniquenessTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/keys" VERSION "1" =
              DOMAIN Number = 0.00..100.00;
              TOPIC T =
                STRUCTURE Entry = kind : TEXT; number : Number; END Entry;
                STRUCTURE Special EXTENDS Entry = number (EXTENDED) : 0.00..50.00; END Special;
                STRUCTURE Group = entries : BAG {0..*} OF Entry RESTRICTION (Special); END Group;
                CLASS Item =
                  entries : BAG {0..*} OF Entry;
                  groups : LIST {0..*} OF Group;
                  UNIQUE Direct: (LOCAL) entries: kind, number;
                  UNIQUE Nested: (LOCAL) groups->entries: kind, number;
                END Item;
              END T;
            END Checks.
            """;

    @Test
    void keepsExactCompositeKeysPerOwnerAndBasket() {
        var f = new Fixture(MODEL);
        var a = f.entry("Entry", "a", "1.0");
        var different = f.entry("Entry", "b", "1.00");
        var duplicate = f.entry("Entry", "a", "1.00");
        var missing = f.entry("Entry", "a", null);
        var empty = f.entry("Entry", "", "1.00");
        var undefined = new TransferValue.Structured(f.ref("Entry"), Map.of(
                f.ref("Entry", "kind"), new TransferValue.Text("a"),
                f.ref("Entry", "number"), TransferValue.Undefined.INSTANCE));
        var invalid = Map.<SymbolRef, TransferValue>of(f.ref("Item", "entries"),
                repeated(CollectionKind.BAG, a, different, duplicate, missing, missing, empty, empty, undefined));
        var single = Map.<SymbolRef, TransferValue>of(f.ref("Item", "entries"), repeated(CollectionKind.BAG, duplicate));
        try (var session = f.session(ValidationOptions.defaults())) {
            var b1 = f.startBasket(session, "b1");
            f.accept(session, b1, "o1", invalid, RUN);
            f.accept(session, b1, "o2", single, RUN);
            session.accept(new TransferEvent.EndBasket(b1), RUN);
            var b2 = f.startBasket(session, "b2");
            f.accept(session, b2, "o3", single, RUN);
            session.accept(new TransferEvent.EndBasket(b2), RUN);
            var report = finish(session);
            assertEquals(1, report.detectedViolations());
            var diagnostic = report.examples().getFirst();
            assertEquals(ValidationCode.LOCAL_UNIQUE_DUPLICATE, diagnostic.code());
            assertEquals(f.constraint("Direct"), diagnostic.rule().declaration());
            assertTrue(diagnostic.rule().range().isPresent());
            assertEquals("o1", diagnostic.data().tid().orElseThrow().value());
            assertEquals(List.of(new DataLocation.Slot(f.ref("Item", "entries")), new DataLocation.Index(3)),
                    diagnostic.data().path());
            assertEquals(List.of("1", "3"), diagnostic.arguments());
            assertEquals(List.of("Direct", "Nested").stream().map(f::constraint).toList(),
                    report.coverage().constraints().stream().map(ValidationCoverage.ConstraintCount::constraint).toList());
            assertTrue(report.coverage().constraints().stream().allMatch(count ->
                    count.status() == ValidationCoverage.ConstraintStatus.CHECKED && count.occurrences() == 3));
        }
    }

    @Test
    void checksUnlabelledLegacyLocalUniqueness() {
        var f = new Fixture("""
                INTERLIS 2.3;
                MODEL Checks (en) AT "https://example.org/keys" VERSION "1" =
                  TOPIC T =
                    STRUCTURE Entry = code : (a, b); END Entry;
                    CLASS Item =
                      entries : LIST {0..*} OF Entry;
                      UNIQUE (LOCAL) entries: code;
                    END Item;
                  END T;
                END Checks.
                """);
        var entry = f.ref("Entry");
        var code = f.ref("Entry", "code");
        var a = new TransferValue.Structured(entry, Map.of(code, new TransferValue.Enumeration(List.of("a"))));
        var b = new TransferValue.Structured(entry, Map.of(code, new TransferValue.Enumeration(List.of("b"))));
        var entries = f.ref("Item", "entries");
        assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE,
                f.validate(Map.of(entries, repeated(CollectionKind.LIST, a, b))).result());
        var report = f.validate(Map.of(entries, repeated(CollectionKind.LIST, a, b, a)));
        var diagnostic = report.examples().getFirst();
        assertEquals(ValidationCode.LOCAL_UNIQUE_DUPLICATE, diagnostic.code());
        assertEquals(List.of("1", "3"), diagnostic.arguments());
        assertEquals(List.of(new DataLocation.Slot(entries), new DataLocation.Index(3)), diagnostic.data().path());
    }

    @Test
    void traversesNestedPrefixesAndBindsInheritedFieldsToEffectiveRestrictedSlots() {
        var f = new Fixture(MODEL);
        var a = f.entry("Special", "a", "3");
        var group = f.ref("Group");
        var entries = f.ref("Group", "entries");
        var first = new TransferValue.Structured(group, Map.of(entries, repeated(CollectionKind.BAG, a, a)));
        var second = new TransferValue.Structured(group, Map.of(entries, repeated(CollectionKind.BAG, a)));
        var report = f.validate(Map.of(f.ref("Item", "groups"), repeated(CollectionKind.LIST, first, second)));
        assertEquals(List.of(f.constraint("Nested"), f.constraint("Nested")),
                report.examples().stream().map(d -> d.rule().declaration()).toList());
        assertEquals(List.of("1/1", "2/1"), report.examples().get(1).arguments());
        assertEquals(List.of(new DataLocation.Slot(f.ref("Item", "groups")), new DataLocation.Index(2),
                new DataLocation.Slot(entries), new DataLocation.Index(1)), report.examples().get(1).data().path());
        assertEquals(List.of("1/1", "1/2"), report.examples().getFirst().arguments());
    }

    @Test
    void reportsGlobalRulesAndUnsupportedLocalKeysSeparately() {
        var f = new Fixture("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/keys" VERSION "1" =
                  FUNCTION external(n: NUMERIC): BOOLEAN;
                  DOMAIN Number = 0..100 CONSTRAINTS Future: external(THIS);
                  TOPIC T =
                    CLASS Peer = number : Number; END Peer;
                    STRUCTURE Link = target : REFERENCE TO Peer; point : COORD 0..10, 0..10; END Link;
                    CLASS Item =
                      number : Number;
                      link : Link;
                      items : BAG OF Link;
                      MANDATORY CONSTRAINT External: external(number);
                      UNIQUE Foreign: WHERE link->target->number > 0: (LOCAL) items: point;
                      UNIQUE Coordinates: (LOCAL) items: point;
                      UNIQUE Filtered: WHERE number > 0: (LOCAL) items: point;
                      UNIQUE Global: number;
                      UNIQUE (BASKET) BasketOnly: number;
                      EXISTENCE CONSTRAINT Exists: number REQUIRED IN Peer: number;
                      SET CONSTRAINT SetRule: INTERLIS.objectCount(ALL) > 5;
                      CONSTRAINT Fraction: >= 90% number > 10;
                    END Item;
                  END T;
                END Checks.
                """);
        var report = f.validate(Map.of(f.ref("Item", "number"), number("1")));
        assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, report.result());
        assertEquals(List.of(), report.coverage().constraints());
        var ownerRules = report.coverage().notChecked().stream()
                .filter(rule -> rule.objectType().equals(f.ref("Item")) && rule.slot().isEmpty()).toList();
        assertEquals(List.of("External", "Foreign", "Coordinates", "Filtered", "Global", "BasketOnly",
                        "Exists", "SetRule", "Fraction").stream().map(f::constraint).toList(),
                ownerRules.stream().map(UncheckedRuleInfo::constraint).toList());
        assertTrue(ownerRules.subList(0, 4).stream().allMatch(rule ->
                rule.reason() == UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION));
        assertTrue(ownerRules.subList(4, 9).stream().allMatch(rule ->
                rule.reason() == UncheckedRuleInfo.Reason.OUT_OF_SCOPE_GLOBAL));
        assertTrue(report.coverage().notChecked().stream().anyMatch(rule -> rule.constraint().equals(f.constraint("Future"))
                && rule.slot().equals(Optional.of(f.ref("Item", "number")))
                && rule.reason() == UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION));
        assertEquals(List.of(ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY, ValidationCoverage.RuleFamily.TOPOLOGY),
                report.coverage().unavailableFamilies());
    }

    @Test
    void chargesLocalIndexWorkToTheObjectBudget() {
        var f = new Fixture(MODEL);
        var entries = new TransferValue.Repeated(CollectionKind.BAG,
                java.util.Collections.nCopies(100, f.entry("Entry", "a", "1")));
        var data = Map.<SymbolRef, TransferValue>of(f.ref("Item", "entries"), entries);
        // With these inputs, the value and field walks finish below 750 visits.
        // Evaluating two key paths per entry raises the total above that limit.
        try (var session = f.session(new ValidationOptions(1, 128, 750))) {
            var basket = f.startBasket(session, "b1");
            assertThrows(ValidationLimitException.class, () -> f.accept(session, basket, "o1", data, RUN));
            assertThrows(ValidationStreamException.class, session::finish);
        }
        try (var session = f.session(new ValidationOptions(1, 128, 2_000))) {
            var basket = f.startBasket(session, "b1");
            f.accept(session, basket, "o1", data, RUN);
            session.accept(new TransferEvent.EndBasket(basket), RUN);
            var report = finish(session);
            assertEquals(99, report.detectedViolations());
            assertEquals(1, report.examples().size());
        }
    }

    private static TransferValue.Repeated repeated(CollectionKind kind, TransferValue... values) {
        return new TransferValue.Repeated(kind, List.of(values));
    }

    private static TransferValue.NumberValue number(String value) {
        return new TransferValue.NumberValue(value, new BigDecimal(value));
    }

    private static ValidationReport finish(ValidationSession session) {
        session.accept(new TransferEvent.EndTransfer(), RUN);
        return session.finish();
    }

    private static final class Fixture {
        private final CompiledSchema schema;
        private final ValidationPlan plan;

        Fixture(String text) {
            schema = compile(text);
            plan = ready(schema, transfer(schema));
        }

        SymbolRef ref(String... relative) {
            var path = new java.util.ArrayList<>(List.of("Checks", "T"));
            path.addAll(List.of(relative));
            return schema.queries().findQualifiedName(QualifiedName.of(path.toArray(String[]::new))).getFirst();
        }

        SymbolRef constraint(String name) {
            return schema.semanticGraph().declarationsInOrder().stream()
                    .filter(ConstraintSymbol.class::isInstance).filter(symbol -> symbol.name().value().equals(name))
                    .map(symbol -> plan.ref(symbol.id())).findFirst().orElseThrow();
        }

        TransferValue.Structured entry(String type, String kind, String value) {
            return new TransferValue.Structured(ref(type), value == null
                    ? Map.of(ref("Entry", "kind"), new TransferValue.Text(kind))
                    : Map.of(ref("Entry", "kind"), new TransferValue.Text(kind), ref(type, "number"), number(value)));
        }

        ValidationSession session(ValidationOptions options) {
            var session = LocalValidator.session(plan, options);
            session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of()), RUN);
            return session;
        }

        TransferIdentity.BasketId startBasket(ValidationSession session, String id) {
            var basket = new TransferIdentity.BasketId(id, TransferIdentity.IdOrigin.SOURCE);
            session.accept(new TransferEvent.StartBasket(basket, ref(), List.of(), TransferEvent.BasketKind.FULL,
                    TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), RUN);
            return basket;
        }

        void accept(ValidationSession session, TransferIdentity.BasketId basket, String id,
                    Map<SymbolRef, TransferValue> values, CancellationToken token) {
            var tid = new TransferIdentity.TransferId(id, TransferIdentity.IdOrigin.SOURCE);
            var identity = new TransferIdentity(basket, Optional.empty(), Optional.of(tid), Optional.empty(), Optional.empty());
            var object = new TransferObject(identity, new TransferValue.Structured(ref("Item"), values));
            session.accept(new TransferEvent.ObjectEvent(object, Optional.empty(), TransferEvent.Consistency.COMPLETE), token);
        }

        ValidationReport validate(Map<SymbolRef, TransferValue> values) {
            try (var session = session(ValidationOptions.defaults())) {
                var basket = startBasket(session, "b1");
                accept(session, basket, "o1", values, RUN);
                session.accept(new TransferEvent.EndBasket(basket), RUN);
                return finish(session);
            }
        }
    }
}
