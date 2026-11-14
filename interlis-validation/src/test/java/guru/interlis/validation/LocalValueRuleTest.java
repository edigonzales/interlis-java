package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferEvent.Consistency;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferIdentity.BasketId;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.ready;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class LocalValueRuleTest {
    private final guru.interlis.model.CompiledSchema schema = compile("""
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
              DOMAIN
                Label = MANDATORY TEXT*30;
                ShortLabel EXTENDS Label = TEXT*20;
                CodeOid = OID TEXT*10;
                Point = COORD 0.0..10.0, 0.0..10.0;
              STRUCTURE Detail =
                note : MANDATORY TEXT;
              END Detail;
              STRUCTURE Other =
              END Other;
              TOPIC T =
                CLASS Item =
                  code : ShortLabel;
                  comment : TEXT;
                  copied : MANDATORY TEXT*20 := code;
                  memory (TRANSIENT) : MANDATORY BOOLEAN := #true;
                  bounded : LIST {2..4} OF Detail;
                  open : BAG {0..*} OF Detail;
                  large : BAG {0..2147483648} OF Detail;
                  trace : MANDATORY POLYLINE WITH (STRAIGHTS) VERTEX Point;
                  active : BOOLEAN;
                  count : 0..20;
                  kind : (first, second);
                  binary : BLACKBOX BINARY;
                  xml : BLACKBOX XML;
                  objectId : CodeOid;
                END Item;
              END T;
            END Checks.
            """);
    private final ValidationPlan plan = ready(schema, transfer(schema));
    private final SymbolRef topic = ref("Checks", "T");
    private final SymbolRef item = ref("Checks", "T", "Item");
    private final SymbolRef detail = ref("Checks", "Detail");
    private final SymbolRef note = ref("Checks", "Detail", "note");
    private final SymbolRef code = field("code");
    private final SymbolRef comment = field("comment");
    private final SymbolRef bounded = field("bounded");
    private final SymbolRef open = field("open");
    private final SymbolRef large = field("large");
    private final SymbolRef trace = field("trace");
    private final TransferValue.Structured validDetail =
            new TransferValue.Structured(detail, Map.of(note, new TransferValue.Text("note")));
    private final TransferValue.Repeated validList =
            new TransferValue.Repeated(CollectionKind.LIST, List.of(validDetail, validDetail));

    @Test
    void separatesMissingAndUndefinedAndRetainsDomainProvenance() {
        for (var entry : List.of(
                Map.entry(Optional.<TransferValue>empty(), REQUIRED_VALUE_MISSING),
                Map.entry(Optional.<TransferValue>of(TransferValue.Undefined.INSTANCE), REQUIRED_VALUE_UNDEFINED),
                Map.entry(Optional.<TransferValue>of(new TransferValue.Text("")), REQUIRED_VALUE_UNDEFINED))) {
            Map<SymbolRef, TransferValue> data = new java.util.HashMap<>(Map.of(bounded, validList, field("copied"), new TransferValue.Text("ok")));
            entry.getKey().ifPresent(value -> data.put(code, value));
            var report = validate(Consistency.INCOMPLETE, Consistency.INCOMPLETE, data);
            assertEquals(List.of(entry.getValue()), codes(report));
            assertEquals(ref("Checks", "ShortLabel"), report.examples().getFirst().rule().declaration());
        }
        var space = validate(Consistency.INCOMPLETE, Consistency.INCOMPLETE,
                Map.of(code, new TransferValue.Text(" "), bounded, validList));
        assertEquals(List.of(), codes(space));
    }

    @Test
    void checksExactCollectionBoundsAndCountsDuplicateOccurrences() {
        for (int size : List.of(0, 1, 2, 4, 5)) {
            List<TransferValue> values = java.util.Collections.nCopies(size, validDetail);
            var report = validate(Consistency.INCOMPLETE, Consistency.COMPLETE, Map.of(
                    code, new TransferValue.Text("ok"),
                    bounded, new TransferValue.Repeated(CollectionKind.LIST, values),
                    open, new TransferValue.Repeated(CollectionKind.BAG, values),
                    large, new TransferValue.Repeated(CollectionKind.BAG, values)));
            assertEquals(size < 2 || size > 4 ? List.of(CARDINALITY_VIOLATION) : List.of(), codes(report));
            if (size < 2 || size > 4) {
                assertEquals(List.of(Integer.toString(size), "2", "4"), report.examples().getFirst().arguments());
            }
        }
    }

    @Test
    void reportsWrongFamiliesAndNestedPaths() {
        var wrong = new TransferValue.Structured(ref("Checks", "Other"), Map.of());
        var absent = new TransferValue.Structured(detail, Map.of());
        var report = validate(Consistency.INCOMPLETE, Consistency.COMPLETE, Map.of(
                code, new TransferValue.BooleanValue(true),
                comment, validList,
                bounded, new TransferValue.Repeated(CollectionKind.LIST, List.of(wrong, absent))));
        assertEquals(List.of(TYPE_MISMATCH, TYPE_MISMATCH, STRUCTURE_TYPE_MISMATCH, REQUIRED_VALUE_MISSING), codes(report));
        assertEquals(List.of(new DataLocation.Slot(bounded), new DataLocation.Index(2), new DataLocation.Slot(note)),
                report.examples().getLast().data().path());
    }

    @Test
    void computesAbsentDerivedValueAndChecksSuppliedType() {
        var values = new java.util.HashMap<SymbolRef, TransferValue>(
                Map.of(code, new TransferValue.Text("ok"), bounded, validList));
        var absent = validate(Consistency.INCOMPLETE, Consistency.COMPLETE, values);
        assertEquals(List.of(), codes(absent));
        assertTrue(absent.coverage().missingRuntimeContext().isEmpty());
        values.put(field("copied"), new TransferValue.BooleanValue(true));
        var present = validate(Consistency.INCOMPLETE, Consistency.COMPLETE, values);
        assertEquals(List.of(TYPE_MISMATCH), codes(present));
        assertTrue(present.coverage().missingRuntimeContext().isEmpty());
    }

    @Test
    void usesDistinctNeutralScalarFamilies() {
        var data = new java.util.LinkedHashMap<SymbolRef, TransferValue>();
        data.put(code, new TransferValue.Text("ok"));
        data.put(bounded, validList);
        data.put(field("active"), new TransferValue.BooleanValue(true));
        data.put(field("count"), new TransferValue.NumberValue("3", java.math.BigDecimal.valueOf(3)));
        data.put(field("kind"), new TransferValue.Enumeration(List.of("first")));
        data.put(field("binary"), new TransferValue.Binary(new byte[0]));
        data.put(field("xml"), new TransferValue.XmlFragment(""));
        data.put(field("objectId"), new TransferValue.Oid("abc"));
        assertTrue(codes(validate(Consistency.INCOMPLETE, Consistency.COMPLETE, data)).isEmpty());
        var wrong = List.of("active", "count", "kind", "binary", "xml", "objectId");
        for (var name : wrong) {
            data.put(field(name), new TransferValue.Text("wrong"));
        }
        var report = validate(Consistency.INCOMPLETE, Consistency.COMPLETE, data);
        assertEquals(wrong.size(), report.detectedViolations());
        assertTrue(codes(report).stream().allMatch(code -> code == TYPE_MISMATCH));
        assertEquals(wrong.stream().map(this::field).toList(),
                report.examples().stream().map(diagnostic -> diagnostic.rule().declaration()).toList());
    }

    @Test
    void omitsOnlyUncertainGeometricAbsenceInIncompleteTransfer() {
        var data = Map.<SymbolRef, TransferValue>of(code, new TransferValue.Text("ok"), bounded, validList,
                field("copied"), new TransferValue.Text("ok"));
        var complete = validate(Consistency.COMPLETE, Consistency.COMPLETE, data);
        assertEquals(List.of(REQUIRED_VALUE_MISSING), codes(complete));
        assertEquals(trace, complete.examples().getFirst().rule().declaration());

        var incomplete = validate(Consistency.INCOMPLETE, Consistency.COMPLETE, data);
        assertEquals(List.of(), codes(incomplete));
        assertEquals(List.of(new ValidationCoverage.RuleCount(item, trace,
                ValidationCoverage.RuleFamily.DEFINED_VALUE, 1)), incomplete.coverage().incompleteContext());

        var supplied = new java.util.HashMap<>(data);
        supplied.put(trace, new TransferValue.Text("wrong"));
        assertEquals(List.of(TYPE_MISMATCH), codes(validate(Consistency.COMPLETE, Consistency.INCOMPLETE, supplied)));
        assertEquals(List.of(REQUIRED_VALUE_MISSING), codes(validate(Consistency.INCONSISTENT, Consistency.ADAPTED, data)));
    }

    private ValidationReport validate(Consistency basketConsistency, Consistency objectConsistency,
                                      Map<SymbolRef, TransferValue> values) {
        var basket = new BasketId("b", TransferIdentity.IdOrigin.SOURCE);
        var token = CancellationToken.none();
        try (var session = LocalValidator.session(plan, ValidationOptions.defaults())) {
            session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
            session.accept(new TransferEvent.StartBasket(basket, topic, List.of(), TransferEvent.BasketKind.FULL,
                    basketConsistency, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
            var identity = new TransferIdentity(basket, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
            var object = new TransferObject(identity, new TransferValue.Structured(item, values));
            session.accept(new TransferEvent.ObjectEvent(object, Optional.empty(), objectConsistency), token);
            session.accept(new TransferEvent.EndBasket(basket), token);
            session.accept(new TransferEvent.EndTransfer(), token);
            return session.finish();
        }
    }

    private static List<ValidationCode> codes(ValidationReport report) {
        return report.examples().stream().map(ValidationDiagnostic::code).toList();
    }

    private SymbolRef field(String name) {
        return ref("Checks", "T", "Item", name);
    }

    private SymbolRef ref(String... path) {
        return schema.queries().findQualifiedName(QualifiedName.of(path)).getFirst();
    }
}
