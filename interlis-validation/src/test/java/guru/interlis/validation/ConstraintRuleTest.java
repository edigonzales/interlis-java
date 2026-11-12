package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.ValidationCoverage.ConstraintStatus.*;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.ready;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintRuleTest {
    private static final String HEADER = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
            """;

    @Test
    void appliesImplicationAndKeepsUndefinedAndMissingContextDistinct() {
        var f = new Fixture(HEADER + """
                DOMAIN Point = COORD 0..10, 0..10;
                TOPIC T =
                  CLASS Item =
                    state : (ready, draft);
                    point : Point;
                    test : BOOLEAN;
                    amount : 0..100;
                    label : TEXT;
                    derived : TEXT := label;
                    MANDATORY CONSTRAINT RequiresPoint: state == #ready => DEFINED(point);
                    MANDATORY CONSTRAINT OptionalAmount: amount > 10;
                    MANDATORY CONSTRAINT Runtime: test OR DEFINED(derived);
                  END Item;
                END T;
                END Checks.
                """);
        var pass = f.validate(Map.of("state", enumeration("ready"), "point",
                new TransferGeometry.Coordinate(List.of(number("2"), number("3"))), "test", bool(true)));
        assertEquals(List.of(), codes(pass));
        assertEquals(CHECKED, f.status(pass, "RequiresPoint"));
        assertEquals(UNDEFINED_BY_DATA, f.status(pass, "OptionalAmount"));
        assertEquals(CHECKED, f.status(pass, "Runtime"));

        var fail = f.validate(Map.of("state", enumeration("ready"), "test", bool(false)));
        assertEquals(List.of(MANDATORY_CONSTRAINT_FALSE), codes(fail));
        assertEquals(f.constraint("RequiresPoint"), fail.examples().getFirst().rule().declaration());
        assertTrue(fail.examples().getFirst().rule().range().isPresent());
        assertTrue(fail.examples().getFirst().data().path().isEmpty());
        assertEquals(MISSING_RUNTIME_CONTEXT, f.status(fail, "Runtime"));

        var draft = f.validate(Map.of("state", enumeration("draft"), "test", bool(true)));
        assertTrue(draft.examples().isEmpty());
        assertEquals(CHECKED, f.status(draft, "RequiresPoint"));
    }

    @Test
    void definedTreatsTextWithCanonicalLengthZeroAsUndefined() {
        var f = new Fixture(HEADER + """
                TOPIC T =
                  CLASS Item =
                    label : TEXT;
                    MANDATORY CONSTRAINT HasLabel: DEFINED(label);
                  END Item;
                END T;
                END Checks.
                """);
        assertEquals(List.of(), codes(f.validate(Map.of("label", new TransferValue.Text("a\u0301")))));
        var report = f.validate(Map.of("label", new TransferValue.Text("\u0301")));
        assertEquals(List.of(MANDATORY_CONSTRAINT_FALSE), codes(report));
        assertEquals(f.constraint("HasLabel"), report.examples().getFirst().rule().declaration());
        assertEquals(CHECKED, f.status(report, "HasLabel"));
    }

    @Test
    void resolvesOverrideSlotsAndReportsEachDomainUseSeparately() {
        var f = new Fixture(HEADER + """
                DOMAIN Amount = 0.00..100.00 CONSTRAINTS Positive: THIS > 10;
                DOMAIN Narrow EXTENDS Amount = 0.00..50.00;
                TOPIC T =
                  CLASS Base (ABSTRACT) =
                    left : Amount;
                    right : Amount;
                  END Base;
                  CLASS Item EXTENDS Base =
                    left (EXTENDED) : Narrow;
                  END Item;
                  CONSTRAINTS OF Base =
                    MANDATORY CONSTRAINT EqualValues: left == right;
                  END;
                END T;
                END Checks.
                """);
        var pass = f.validate(Map.of("left", number("12.0"), "right", number("12.00")));
        assertEquals(List.of(), codes(pass));
        assertEquals(3, pass.coverage().constraints().size());
        var fail = f.validate(Map.of("left", number("2"), "right", number("3")));
        assertEquals(List.of(DOMAIN_CONSTRAINT_FALSE, DOMAIN_CONSTRAINT_FALSE, MANDATORY_CONSTRAINT_FALSE), codes(fail));
        assertEquals(List.of(new DataLocation.Slot(f.field("left"))), fail.examples().getFirst().data().path());
        assertEquals(List.of(new DataLocation.Slot(f.field("right"))), fail.examples().get(1).data().path());
        assertEquals(fail.examples().getFirst().rule().declaration(), fail.examples().get(1).rule().declaration());
        assertEquals(1, fail.coverage().constraints().stream().filter(c -> c.slot().isEmpty()).count());
    }

    @Test
    void resolvesListIndicesAndEvaluatesConstraintsInTheirNestedScope() {
        var f = new Fixture(HEADER + """
                TOPIC T =
                  STRUCTURE Detail =
                    label : TEXT;
                    MANDATORY CONSTRAINT NotEmpty: DEFINED(label);
                  END Detail;
                  CLASS Item =
                    items : LIST {0..*} OF Detail;
                    MANDATORY CONSTRAINT Starts: items[FIRST]->label == "a";
                    MANDATORY CONSTRAINT Ends: items[LAST]->label == "b";
                    MANDATORY CONSTRAINT Second: items[2]->label == "b";
                    MANDATORY CONSTRAINT Far: NOT (DEFINED(items[2147483649]->label));
                  END Item;
                END T;
                END Checks.
                """);
        var detail = f.ref("Checks", "T", "Detail");
        var label = f.ref("Checks", "T", "Detail", "label");
        var first = new TransferValue.Structured(detail, Map.of(label, new TransferValue.Text("a")));
        var last = new TransferValue.Structured(detail, Map.of(label, new TransferValue.Text("b")));
        var missing = new TransferValue.Structured(detail, Map.of());
        var pass = f.validate(Map.of("items", new TransferValue.Repeated(CollectionKind.LIST, List.of(first, last))));
        assertEquals(List.of(), codes(pass));
        assertEquals(CHECKED, f.status(pass, "Far"));
        var fail = f.validate(Map.of("items", new TransferValue.Repeated(CollectionKind.LIST, List.of(first, missing))));
        assertEquals(List.of(MANDATORY_CONSTRAINT_FALSE), codes(fail));
        assertEquals(List.of(new DataLocation.Slot(f.field("items")), new DataLocation.Index(2)),
                fail.examples().getFirst().data().path());
        assertEquals(UNDEFINED_BY_DATA, f.status(fail, "Ends"));
        assertEquals(UNDEFINED_BY_DATA, f.status(fail, "Second"));
    }

    @Test
    void unknownFunctionAndForeignPathRemainUncheckedWhileDivisionIsEvaluated() {
        var f = new Fixture(HEADER + """
                FUNCTION external(x: NUMERIC): BOOLEAN;
                TOPIC T =
                  CLASS Peer = amount : 0..100; END Peer;
                  STRUCTURE Link = peer : REFERENCE TO Peer; END Link;
                  CLASS Item =
                    link : Link;
                    amount : 0..100;
                    divisor : 0..10;
                    label : MANDATORY TEXT;
                    MANDATORY CONSTRAINT Unbound: external(amount);
                    MANDATORY CONSTRAINT Remote: link->peer->amount > 0;
                    MANDATORY CONSTRAINT Quotient: (amount / divisor) > 0;
                    MANDATORY CONSTRAINT Local: amount > 0;
                  END Item;
                END T;
                END Checks.
                """);
        var report = f.validate(Map.of("divisor", number("0")));
        assertEquals(List.of(REQUIRED_VALUE_MISSING), codes(report));
        assertEquals(UNDEFINED_BY_DATA, f.status(report, "Local"));
        assertEquals(UNDEFINED_BY_DATA, f.status(report, "Quotient"));
        assertEquals(List.of(f.constraint("Unbound"), f.constraint("Remote")),
                report.coverage().notChecked().stream().map(UncheckedRuleInfo::constraint).toList());
        assertTrue(report.coverage().notChecked().stream()
                .allMatch(rule -> rule.reason() == UncheckedRuleInfo.Reason.UNSUPPORTED_EXPRESSION));
    }

    @Test
    void treatsAnUndefinedComparisonAsSatisfiedInTheLegacyProfile() {
        var f = new Fixture(HEADER.replace("2.4", "2.3") + """
                TOPIC T = CLASS Item = amount : 0..10; MANDATORY CONSTRAINT amount > 0; END Item; END T;
                END Checks.
                """);
        var absent = f.validate(Map.of());
        assertTrue(absent.examples().isEmpty());
        assertEquals(UNDEFINED_BY_DATA, absent.coverage().constraints().getFirst().status());
        assertEquals(List.of(MANDATORY_CONSTRAINT_FALSE), codes(f.validate(Map.of("amount", number("0")))));
    }

    @Test
    void comparesExplicitUndefinedAndUsesDeclaredEnumerationOrder() {
        var f = new Fixture(HEADER + """
                DOMAIN Progress = (zeta, alpha) ORDERED;
                TOPIC T =
                  CLASS Item =
                    progress : Progress;
                    absent : TEXT;
                    MANDATORY CONSTRAINT Order: progress > #zeta;
                    MANDATORY CONSTRAINT Missing: absent == UNDEFINED;
                  END Item;
                END T;
                END Checks.
                """);
        var pass = f.validate(Map.of("progress", enumeration("alpha")));
        assertEquals(List.of(), codes(pass));
        assertEquals(CHECKED, f.status(pass, "Order"));
        assertEquals(CHECKED, f.status(pass, "Missing"));
        assertEquals(List.of(MANDATORY_CONSTRAINT_FALSE, MANDATORY_CONSTRAINT_FALSE),
                codes(f.validate(Map.of("progress", enumeration("zeta"), "absent", new TransferValue.Text("value")))));
    }

    private static TransferValue.NumberValue number(String value) {
        return new TransferValue.NumberValue(value, new BigDecimal(value));
    }

    private static TransferValue.BooleanValue bool(boolean value) {
        return new TransferValue.BooleanValue(value);
    }

    private static TransferValue.Enumeration enumeration(String... path) {
        return new TransferValue.Enumeration(List.of(path));
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }

    private static final class Fixture {
        private final CompiledSchema schema;
        private final ValidationPlan plan;
        private final SymbolRef item;

        Fixture(String model) {
            schema = compile(model);
            plan = ready(schema, transfer(schema));
            item = ref("Checks", "T", "Item");
        }

        SymbolRef ref(String... path) {
            return schema.queries().findQualifiedName(QualifiedName.of(path)).getFirst();
        }

        SymbolRef field(String name) {
            var slot = plan.object(item).fields().stream().filter(field -> field.slot().attribute().name().value().equals(name))
                    .findFirst().orElseThrow();
            return slot.slot().symbol();
        }

        SymbolRef constraint(String name) {
            var declaration = schema.semanticGraph().declarationsInOrder().stream()
                    .filter(guru.interlis.model.ConstraintSymbol.class::isInstance)
                    .filter(symbol -> symbol.name().value().equals(name)).findFirst().orElseThrow();
            return plan.ref(declaration.id());
        }

        ValidationCoverage.ConstraintStatus status(ValidationReport report, String name) {
            return report.coverage().constraints().stream().filter(count -> count.constraint().equals(constraint(name)))
                    .findFirst().orElseThrow().status();
        }

        ValidationReport validate(Map<String, TransferValue> supplied) {
            var values = new LinkedHashMap<SymbolRef, TransferValue>();
            supplied.forEach((name, value) -> values.put(field(name), value));
            var token = CancellationToken.none();
            var basket = new TransferIdentity.BasketId("b", TransferIdentity.IdOrigin.SOURCE);
            var identity = new TransferIdentity(basket, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            var object = new TransferObject(identity, new TransferValue.Structured(item, values));
            try (var session = LocalValidator.session(plan, ValidationOptions.defaults())) {
                session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
                session.accept(new TransferEvent.StartBasket(basket, ref("Checks", "T"), List.of(), TransferEvent.BasketKind.FULL,
                        TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
                session.accept(new TransferEvent.ObjectEvent(object, Optional.empty(), TransferEvent.Consistency.COMPLETE), token);
                session.accept(new TransferEvent.EndBasket(basket), token);
                session.accept(new TransferEvent.EndTransfer(), token);
                return session.finish();
            }
        }
    }
}
