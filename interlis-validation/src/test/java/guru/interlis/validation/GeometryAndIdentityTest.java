package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferIdentity.ObjectOid;
import guru.interlis.transfer.TransferIdentity.TransferId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.ready;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class GeometryAndIdentityTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final String HEADER = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
            """;
    private static final String GENERICS = HEADER + """
            DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                   A = COORD 0.00..100.00, 0.00..100.00;
                   B = COORD 200.00..300.00, 200.00..300.00;
            CONTEXT Default = G = A OR B;
            TOPIC T =
              DEFERRED GENERICS G;
              STRUCTURE Detail = firstPoint : G; END Detail;
              CLASS Base (ABSTRACT) = details : Detail; secondPoint : G; END Base;
              CLASS Item EXTENDS Base = END Item;
            END T;
            END Checks.
            """;

    @Test
    void resolvesInheritedGenericDomainsPerBasketAndSession() {
        var fixture = new Fixture(GENERICS);
        var item = fixture.ref("Checks", "T", "Item");
        var detail = fixture.ref("Checks", "T", "Detail");
        var firstPoint = fixture.ref("Checks", "T", "Detail", "firstPoint");
        var secondPoint = fixture.ref("Checks", "T", "Base", "secondPoint");
        var details = fixture.ref("Checks", "T", "Base", "details");
        var point = coord("10", "20");
        var values = Map.<SymbolRef, TransferValue>of(
                details, new TransferValue.Structured(detail, Map.of(firstPoint, point)), secondPoint, point);
        var a = fixture.basket("a", List.of(fixture.binding("A")), Optional.empty(), TransferEvent.Consistency.COMPLETE);
        var b = fixture.basket("b", List.of(fixture.binding("B")), Optional.empty(), TransferEvent.Consistency.COMPLETE);
        try (var first = fixture.session(); var second = fixture.session()) {
            first.accept(a, RUN);
            second.accept(b, RUN);
            first.accept(object(a, item, values, Optional.empty(), Optional.empty()), RUN);
            second.accept(object(b, item, values, Optional.empty(), Optional.empty()), RUN);
            first.accept(new TransferEvent.EndBasket(a.id()), RUN);
            second.accept(new TransferEvent.EndBasket(b.id()), RUN);
            first.accept(b, RUN);
            first.accept(object(b, item, values, Optional.empty(), Optional.empty()), RUN);
            first.accept(new TransferEvent.EndBasket(b.id()), RUN);
            var report = finish(first);
            assertEquals(4, report.detectedViolations());
            assertTrue(report.examples().stream().allMatch(d -> d.code() == VALUE_OUT_OF_RANGE && d.data().basket().equals(b.id())));
            assertEquals(List.of(new DataLocation.Slot(details), new DataLocation.Slot(firstPoint),
                            new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.AXIS, 1)),
                    report.examples().getFirst().data().path());
            assertEquals(4, finish(second).detectedViolations());
        }
        var missing = fixture.basket("m", List.of(), Optional.empty(), TransferEvent.Consistency.COMPLETE);
        try (var session = fixture.session()) {
            session.accept(missing, RUN);
            session.accept(object(missing, item, values, Optional.empty(), Optional.empty()), RUN);
            session.accept(new TransferEvent.EndBasket(missing.id()), RUN);
            var report = finish(session);
            assertTrue(report.examples().isEmpty());
            assertEquals(2, report.coverage().missingRuntimeContext().size());
        }
        var wrong = fixture.basket("bad", List.of(new TransferEvent.DomainBinding(fixture.ref("Checks", "G"),
                fixture.ref("Checks", "G"))), Optional.empty(), TransferEvent.Consistency.COMPLETE);
        try (var session = fixture.session()) {
            assertThrows(ValidationStreamException.class, () -> session.accept(wrong, RUN));
        }
    }

    @Test
    void checksCoordinateAxesAndSegmentFormsWithoutCheckingTopology() {
        var fixture = new Fixture(HEADER + """
                DOMAIN Point = COORD 0.00..10.00, 0.00..10.00;
                TOPIC T =
                  CLASS Item =
                    point : Point;
                    many : MULTICOORD 0.00..10.00, 0.00..10.00, 0.00..10.00;
                    trace : POLYLINE WITH (STRAIGHTS) VERTEX Point;
                    curve : POLYLINE WITH (ARCS) VERTEX Point;
                    area : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01;
                  END Item;
                END T;
                END Checks.
                """);
        var item = fixture.ref("Checks", "T", "Item");
        var from = coord("0", "0");
        var open = new TransferGeometry.Polyline(List.of(new TransferGeometry.CurvePart(from,
                List.of(new TransferGeometry.Straight(coord("11", "1"))))));
        var arc = new TransferGeometry.Polyline(List.of(new TransferGeometry.CurvePart(from, List.of(
                new TransferGeometry.Arc(coord("0.0001", "0.98765"), coord("1", "1"),
                        Optional.of(number("-3.50")))))));
        var values = Map.<SymbolRef, TransferValue>of(
                fixture.field("point"), coord("0", "0", "0"),
                fixture.field("many"), new TransferGeometry.MultiCoordinate(List.of(
                        coord("0", "10", "1"), coord("1", "2", "-0.01"))),
                fixture.field("trace"), arc,
                fixture.field("curve"), arc,
                fixture.field("area"), new TransferGeometry.Surface(new TransferGeometry.Ring(List.of(open)), List.of()));
        var report = fixture.validate(item, values);
        assertEquals(List.of(COORDINATE_DIMENSION_MISMATCH, VALUE_OUT_OF_RANGE, SEGMENT_FORM_NOT_ALLOWED, VALUE_OUT_OF_RANGE),
                codes(report));
        assertTrue(report.coverage().unavailableFamilies().contains(ValidationCoverage.RuleFamily.TOPOLOGY));
        var sliced = Map.<SymbolRef, TransferValue>of(fixture.field("trace"),
                new TransferGeometry.MultiPolyline(List.of(open)));
        assertEquals(List.of(TYPE_MISMATCH), codes(fixture.validate(item, sliced)));
        var basket = fixture.basket("slice", List.of(), Optional.empty(), TransferEvent.Consistency.INCOMPLETE);
        try (var session = fixture.session()) {
            session.accept(basket, RUN);
            session.accept(object(basket, item, sliced, Optional.empty(), Optional.empty()), RUN);
            session.accept(new TransferEvent.EndBasket(basket.id()), RUN);
            assertEquals(List.of(VALUE_OUT_OF_RANGE), codes(finish(session)));
        }
    }

    @Test
    void checksObjectBasketAndAttributeOidDomainsWithoutGlobalUniqueness() {
        var fixture = new Fixture(HEADER + """
                DOMAIN NumberId = OID 1..20;
                       TextId = OID TEXT*3;
                TOPIC T =
                  BASKET OID AS TextId;
                  CLASS Item =
                    OID AS NumberId;
                    explicitId : TextId;
                    uuid : INTERLIS.UUIDOID;
                  END Item;
                  ASSOCIATION Link =
                    left -- {0..*} Item;
                    right -- {0..*} Item;
                  END Link;
                END T;
                END Checks.
                """);
        var item = fixture.ref("Checks", "T", "Item");
        var link = fixture.ref("Checks", "T", "Link");
        var a = fixture.basket("a", List.of(), Optional.of("too-long"), TransferEvent.Consistency.COMPLETE);
        var b = fixture.basket("b", List.of(), Optional.of("B02"), TransferEvent.Consistency.COMPLETE);
        var values = Map.<SymbolRef, TransferValue>of(fixture.field("explicitId"), new TransferValue.Oid("abcd"),
                fixture.field("uuid"), new TransferValue.Oid("not-a-uuid"));
        try (var session = fixture.session()) {
            session.accept(a, RUN);
            session.accept(object(a, item, values, Optional.of("t1"), Optional.of("21")), RUN);
            session.accept(object(a, link, Map.of(), Optional.empty(), Optional.empty()), RUN);
            session.accept(new TransferEvent.EndBasket(a.id()), RUN);
            session.accept(b, RUN);
            session.accept(object(b, item, Map.of(), Optional.of("t2"), Optional.of("20")), RUN);
            session.accept(object(b, item, Map.of(), Optional.of("t3"), Optional.of("20")), RUN);
            session.accept(object(b, item, Map.of(), Optional.empty(), Optional.of("20")), RUN);
            session.accept(object(b, item, Map.of(), Optional.empty(), Optional.empty()), RUN);
            session.accept(new TransferEvent.EndBasket(b.id()), RUN);
            var report = finish(session);
            assertEquals(List.of(INVALID_OID, VALUE_OUT_OF_RANGE, INVALID_OID, INVALID_OID,
                    REQUIRED_TID_MISSING, REQUIRED_OID_MISSING), codes(report));
            assertTrue(report.examples().getFirst().data().objectType().isEmpty());
        }
    }

    @Test
    void checksEffectiveStructureSlotsAndLeavesReferenceTargetExistenceOutsideScope() {
        var fixture = new Fixture(HEADER + """
                TOPIC T =
                  CLASS Target = END Target;
                  STRUCTURE Base = note : MANDATORY TEXT*20; END Base;
                  STRUCTURE Narrow EXTENDS Base =
                    note (EXTENDED) : MANDATORY TEXT*2;
                    ref : REFERENCE TO Target;
                  END Narrow;
                  STRUCTURE Further EXTENDS Narrow = END Further;
                  STRUCTURE Sibling EXTENDS Base = END Sibling;
                  CLASS Item =
                    detail : Base RESTRICTION (Narrow);
                    names : CLASS RESTRICTION (Target);
                  END Item;
                END T;
                END Checks.
                """);
        var item = fixture.ref("Checks", "T", "Item");
        var further = fixture.ref("Checks", "T", "Further");
        var sibling = fixture.ref("Checks", "T", "Sibling");
        var note = fixture.ref("Checks", "T", "Narrow", "note");
        var ref = fixture.ref("Checks", "T", "Narrow", "ref");
        var reference = new TransferValue.Reference(new TransferId("absent", TransferIdentity.IdOrigin.SOURCE),
                Optional.empty(), false, Optional.empty());
        var good = new TransferValue.Structured(further, Map.of(note, new TransferValue.Text("ok"), ref, reference));
        var name = new TransferValue.ClassName(QualifiedName.of("Checks", "T", "Target"));
        var report = fixture.validate(item, Map.of(fixture.field("detail"), good, fixture.field("names"), name));
        assertEquals(List.of(), codes(report));
        assertTrue(report.coverage().unavailableFamilies().contains(ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY));
        var invalid = new TransferValue.Structured(further, Map.of(note, new TransferValue.Text("long"), ref, reference));
        assertEquals(List.of(TEXT_TOO_LONG), codes(fixture.validate(item, Map.of(fixture.field("detail"), invalid))));
        assertEquals(List.of(STRUCTURE_TYPE_MISMATCH), codes(fixture.validate(item,
                Map.of(fixture.field("detail"), new TransferValue.Structured(sibling, Map.of())))));
        assertEquals(List.of(MODEL_VALUE_NOT_ALLOWED), codes(fixture.validate(item, Map.of(fixture.field("names"),
                new TransferValue.ClassName(QualifiedName.of("Checks", "T", "Item"))))));
    }

    @Test
    void appliesImportedInheritedMandatoryDomainOnceInExtendedTopic() {
        var fixture = new Fixture(ValidationPlannerTest.compileWithOriginImport("""
                INTERLIS 2.4;
                MODEL Origin (en) AT "https://example.org/origin" VERSION "1" =
                  DOMAIN Required = MANDATORY TEXT*20;
                  TOPIC BaseTopic (ABSTRACT) = CLASS Base (ABSTRACT) = label : Required; END Base; END BaseTopic;
                END Origin.
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  IMPORTS Origin;
                  DOMAIN Narrow EXTENDS Origin.Required = TEXT*3;
                  TOPIC T EXTENDS Origin.BaseTopic =
                    CLASS Item EXTENDS Origin.BaseTopic.Base =
                      label (EXTENDED) : Narrow;
                    END Item;
                  END T;
                END Checks.
                """));
        var item = fixture.ref("Checks", "T", "Item");
        var missing = fixture.validate(item, Map.of());
        assertEquals(List.of(REQUIRED_VALUE_MISSING), codes(missing));
        assertEquals(fixture.ref("Checks", "Narrow"), missing.examples().getFirst().rule().declaration());
        assertEquals(List.of(TEXT_TOO_LONG), codes(fixture.validate(item,
                Map.of(fixture.field("label"), new TransferValue.Text("long")))));
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }

    private static TransferValue.NumberValue number(String value) {
        return new TransferValue.NumberValue(value, new BigDecimal(value));
    }

    private static TransferGeometry.Coordinate coord(String... ordinates) {
        return new TransferGeometry.Coordinate(java.util.Arrays.stream(ordinates).map(GeometryAndIdentityTest::number).toList());
    }

    private static TransferEvent.ObjectEvent object(TransferEvent.StartBasket basket, SymbolRef type,
                                                     Map<SymbolRef, TransferValue> values,
                                                     Optional<String> tid, Optional<String> oid) {
        var identity = new TransferIdentity(basket.id(), Optional.empty(),
                tid.map(value -> new TransferId(value, TransferIdentity.IdOrigin.SOURCE)), oid.map(ObjectOid::new), Optional.empty());
        return new TransferEvent.ObjectEvent(new TransferObject(identity, new TransferValue.Structured(type, values)),
                Optional.empty(), TransferEvent.Consistency.COMPLETE);
    }

    private static ValidationReport finish(ValidationSession session) {
        session.accept(new TransferEvent.EndTransfer(), RUN);
        return session.finish();
    }

    private static final class Fixture {
        private final CompiledSchema schema;
        private final ValidationPlan plan;

        Fixture(String model) {
            this(compile(model));
        }

        Fixture(CompiledSchema schema) {
            this.schema = schema;
            plan = ready(schema, transfer(schema));
        }

        SymbolRef ref(String... name) {
            return schema.queries().findQualifiedName(QualifiedName.of(name)).getFirst();
        }

        SymbolRef field(String name) {
            return ref("Checks", "T", "Item", name);
        }

        TransferEvent.DomainBinding binding(String concrete) {
            return new TransferEvent.DomainBinding(ref("Checks", "G"), ref("Checks", concrete));
        }

        TransferEvent.StartBasket basket(String id, List<TransferEvent.DomainBinding> domains,
                                         Optional<String> oid, TransferEvent.Consistency consistency) {
            return new TransferEvent.StartBasket(new BasketId(id, TransferIdentity.IdOrigin.SOURCE), ref("Checks", "T"),
                    List.of(), TransferEvent.BasketKind.FULL, consistency, Optional.empty(), Optional.empty(), oid, domains);
        }

        ValidationSession session() {
            var session = LocalValidator.session(plan, ValidationOptions.defaults());
            session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of()), RUN);
            return session;
        }

        ValidationReport validate(SymbolRef type, Map<SymbolRef, TransferValue> fields) {
            var basket = basket("b", List.of(), Optional.empty(), TransferEvent.Consistency.COMPLETE);
            try (var session = session()) {
                session.accept(basket, RUN);
                session.accept(object(basket, type, fields, Optional.empty(), Optional.empty()), RUN);
                session.accept(new TransferEvent.EndBasket(basket.id()), RUN);
                return finish(session);
            }
        }
    }
}
