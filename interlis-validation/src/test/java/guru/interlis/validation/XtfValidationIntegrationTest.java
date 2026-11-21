package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
import guru.interlis.transfer.TransferGeometry;
import guru.interlis.transfer.TransferIdentity;
import guru.interlis.transfer.TransferObject;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferProtocolException;
import guru.interlis.transfer.TransferReader;
import guru.interlis.transfer.TransferValue;
import guru.interlis.xtf23.Xtf23Exception;
import guru.interlis.xtf23.Xtf23Reader;
import guru.interlis.xtf24.Xtf24Exception;
import guru.interlis.xtf24.Xtf24Reader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.validation.ValidationCode.*;
import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class XtfValidationIntegrationTest {
    private static final CancellationToken RUN = CancellationToken.none();
    private static final ValidationOptions OPTIONS = ValidationOptions.defaults();

    @Test
    void appliesLineAttributeRulesAndBudgetsToDecodedValues() {
        var schema = compile("""
                INTERLIS 2.3;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  DOMAIN Point = COORD 0..100, 0..100;
                  TOPIC Data =
                    CLASS Target = level : 0..10; END Target;
                    STRUCTURE Details = level : 0..10; END Details;
                    STRUCTURE Marks =
                      label : MANDATORY TEXT*20;
                      details : BAG {0..*} OF Details;
                      link : REFERENCE TO Target;
                      MANDATORY CONSTRAINT link->level == 5;
                    END Marks;
                    CLASS Item =
                      shape : SURFACE WITH (STRAIGHTS) VERTEX Point WITHOUT OVERLAPS > 0.01 LINE ATTRIBUTES Marks;
                    END Item;
                  END Data;
                END Checks.
                """);
        var transfer = transfer(schema);
        var plan = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test">
                    <MODELS><MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/></MODELS>
                  </HEADERSECTION>
                  <DATASECTION><Checks.Data BID="basket">
                    <Checks.Data.Item TID="item"><shape><SURFACE><BOUNDARY><POLYLINE>
                      <LINEATTR><Checks.Data.Marks><label>valid</label><details>
                        <Checks.Data.Details><level>10.4</level></Checks.Data.Details>
                        <Checks.Data.Details><level>10.7</level></Checks.Data.Details>
                      </details><link REF="%s"/></Checks.Data.Marks></LINEATTR>
                      <COORD><C1>0</C1><C2>0</C2></COORD><COORD><C1>10</C1><C2>0</C2></COORD>
                      <COORD><C1>10</C1><C2>10</C2></COORD><COORD><C1>0</C1><C2>0</C2></COORD>
                    </POLYLINE></BOUNDARY></SURFACE></shape></Checks.Data.Item>
                    <Checks.Data.Target TID="target"><level>7</level></Checks.Data.Target>
                  </Checks.Data></DATASECTION>
                </TRANSFER>
                """;
        var primary = new InputDescriptor(new InputId("primary"), InputRole.PRIMARY);
        var limits = new ValidationOptions(10, 3, 1_000, false);
        try (var reader = new Xtf23Reader(transfer, xtfInput(input.formatted("target")))) {
            var error = assertThrows(ValidationLimitException.class,
                    () -> LocalValidator.validate(ready(schema, transfer), reader, limits, RUN));
            assertEquals("Maximum value depth exceeded", error.getMessage());
        }
        var defaults = DatasetOptions.defaults();
        var limited = new DatasetOptions(limits, defaults.limits(), defaults.scope());
        try (var reader = new Xtf23Reader(transfer, xtfInput(input.formatted("target")));
             var storage = new InMemoryValidationStorage(100, 1_000_000);
             var session = DatasetValidator.session(plan, limited, storage)) {
            var error = assertThrows(ValidationLimitException.class, () -> session.readInput(
                    new InputDescriptor(new InputId("reference"), InputRole.REFERENCE), reader, RUN));
            assertEquals("Dataset value traversal budget exceeded", error.getMessage());
        }

        for (var target : List.of("target", "missing")) {
            try (var reader = new Xtf23Reader(transfer, xtfInput(input.formatted(target)));
                 var storage = new InMemoryValidationStorage(100, 1_000_000);
                 var session = DatasetValidator.session(plan, defaults, storage)) {
                session.readInput(primary, reader, RUN);
                var report = session.finish(RUN);
                assertEquals(2, report.inspectedObjects());
                assertEquals(List.of(VALUE_OUT_OF_RANGE,
                                target.equals("target") ? MANDATORY_CONSTRAINT_FALSE : REFERENCE_TARGET_MISSING),
                        report.examples().stream().map(ValidationDiagnostic::code).toList());
                var level = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Details", "level"))
                        .getFirst();
                var diagnostic = report.examples().getFirst();
                assertEquals(level, diagnostic.rule().declaration());
                assertEquals("item", diagnostic.data().tid().orElseThrow().value());
                assertEquals(List.of(new DataLocation.Index(2), new DataLocation.Slot(level)),
                        diagnostic.data().path().subList(4, 6));
                var coverage = report.coverage();
                assertEquals(List.of(ValidationCoverage.RuleFamily.TOPOLOGY),
                        coverage.unsupportedValues().stream().map(ValidationCoverage.RuleCount::family).toList());
                assertTrue(coverage.checked().stream().anyMatch(rule -> rule.slot().equals(level)
                        && rule.family() == ValidationCoverage.RuleFamily.VALUE_RANGE && rule.occurrences() == 2));
                assertEquals(1, coverage.constraints().getFirst().occurrences());
            }
        }
    }

    private static InputStream xtfInput(String source) {
        return new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void checksOidDomainsFromXtf23AdditionalTopics() {
        var schema = compile("""
                INTERLIS 2.3;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  DOMAIN ShortId = OID TEXT*3;
                         ObjectId = OID 1..20;
                  TOPIC Base =
                    CLASS BaseItem = END BaseItem;
                  END Base;
                  TOPIC Extended EXTENDS Base =
                    BASKET OID AS ShortId;
                    OID AS ObjectId;
                    CLASS Item = END Item;
                  END Extended;
                  TOPIC Further EXTENDS Extended = END Further;
                END Checks.
                """);
        var transfer = transfer(schema);
        var plan = ready(schema, transfer);
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test">
                    <MODELS><MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/></MODELS>
                    <OIDSPACES><OIDSPACE NAME="ids" OIDDOMAIN="Checks.ObjectId"/>
                      <OIDSPACE NAME="bid" OIDDOMAIN="Checks.ShortId"/></OIDSPACES>
                  </HEADERSECTION>
                  <DATASECTION>
                    <Checks.Base BID="b1" TOPICS="Checks.Extended,Checks.Further">
                      <Checks.Base.BaseItem TID="plain"/>
                      <Checks.Extended.Item TID="ids:21"/>
                    </Checks.Base>
                    <Checks.Base BID="bid:abcd" TOPICS="Checks.Extended">
                      <Checks.Extended.Item TID="ids:20"/>
                    </Checks.Base>
                    <Checks.Base BID="bid:abc" TOPICS="Checks.Extended"/>
                    <Checks.Extended BID="bid:def">
                      <Checks.Base.BaseItem TID="inherited"/>
                    </Checks.Extended>
                  </DATASECTION>
                </TRANSFER>
                """;
        try (var reader = new Xtf23Reader(transfer, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))) {
            var report = LocalValidator.validate(plan, reader, OPTIONS, RUN);
            var findings = report.examples();
            assertEquals(List.of(VALUE_OUT_OF_RANGE, INVALID_OID),
                    findings.stream().map(ValidationDiagnostic::code).toList());
            assertEquals(2, report.detectedViolations());
            assertEquals(List.of("b1", "bid:abcd"),
                    findings.stream().map(d -> d.data().basket().orElseThrow().value()).toList());
            var basketDomain = schema.queries().findQualifiedName(QualifiedName.of("Checks", "ShortId")).getFirst();
            var objectDomain = schema.queries().findQualifiedName(QualifiedName.of("Checks", "ObjectId")).getFirst();
            assertEquals(List.of(objectDomain, basketDomain),
                    findings.stream().map(d -> d.rule().declaration()).toList());
            assertTrue(findings.getLast().data().objectType().isEmpty());
            assertEquals("ids:21", findings.getFirst().data().tid().orElseThrow().value());
        }
    }

    @Test
    void checksInheritedUuidDomainsInFullXtf23WithoutOidSpaces() {
        var schema = compile("""
                INTERLIS 2.3;
                CONTRACTED MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Base =
                    BASKET OID AS INTERLIS.UUIDOID;
                    OID AS INTERLIS.UUIDOID;
                    CLASS Item = END Item;
                  END Base;
                  TOPIC Extended EXTENDS Base = END Extended;
                END Checks.
                """);
        var transfer = transfer(schema);
        var plan = ready(schema, transfer);
        var input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test">
                    <MODELS><MODEL NAME="Checks" VERSION="1" URI="https://example.org/checks"/></MODELS>
                  </HEADERSECTION>
                  <DATASECTION>
                    <Checks.Extended BID="%s">
                      <Checks.Base.Item TID="%s"/>
                    </Checks.Extended>
                  </DATASECTION>
                </TRANSFER>
                """;
        var valid = input.formatted("C09B1740-5C5A-48EA-B095-938D1C946974",
                "e3e99bf0-5db5-445c-a375-5ae7dc6accE2");
        try (var reader = new Xtf23Reader(transfer, new ByteArrayInputStream(valid.getBytes(StandardCharsets.UTF_8)))) {
            var report = LocalValidator.validate(plan, reader, OPTIONS, RUN);
            assertEquals(1, report.inspectedObjects());
            assertEquals(0, report.detectedViolations());
        }

        var malformed = input.formatted("invalid-bid", "invalid-tid");
        try (var reader = new Xtf23Reader(transfer, new ByteArrayInputStream(malformed.getBytes(StandardCharsets.UTF_8)))) {
            var report = LocalValidator.validate(plan, reader, OPTIONS, RUN);
            var findings = report.examples();
            assertEquals(2, report.detectedViolations());
            assertEquals(List.of(INVALID_OID, INVALID_OID), findings.stream().map(ValidationDiagnostic::code).toList());
            var uuid = schema.queries().findQualifiedName(QualifiedName.of("INTERLIS", "UUIDOID")).getFirst();
            assertTrue(findings.stream().allMatch(finding -> finding.rule().declaration().equals(uuid)));
            assertEquals("invalid-bid", findings.getFirst().data().basket().orElseThrow().value());
            assertTrue(findings.getFirst().data().objectType().isEmpty());
            assertEquals("invalid-tid", findings.getLast().data().tid().orElseThrow().value());
            assertTrue(findings.getLast().data().oid().isEmpty());
        }
    }

    enum Version {
        XTF23("23"), XTF24("24");

        final String dir;

        Version(String dir) {
            this.dir = dir;
        }

        byte[] fixture(String name) throws IOException {
            try (var input = XtfValidationIntegrationTest.class.getResourceAsStream("/local/" + dir + "/" + name)) {
                assertNotNull(input, name);
                return input.readAllBytes();
            }
        }

        TransferReader reader(TransferPlan plan, InputStream input) {
            return this == XTF23 ? new Xtf23Reader(plan, input) : new Xtf24Reader(plan, input);
        }
    }

    @ParameterizedTest
    @EnumSource(Version.class)
    void readsValidAndInvalidHandwrittenTransfersAndMatchesNeutralValidation(Version version) throws IOException {
        var f = new Fixture(version);
        var valid = f.read(version.fixture("valid.xtf"));
        assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, valid.result());
        assertEquals(1, valid.inspectedObjects());
        assertEquals(0, valid.detectedViolations());
        assertCoverage(f, valid, 1);
        assertEquals(f.neutral(false), valid);

        var invalid = f.read(version.fixture("invalid.xtf"));
        assertEquals(ValidationReport.Result.FAILED_CHECKED_SCOPE, invalid.result());
        assertEquals(2, invalid.inspectedObjects());
        assertEquals(6, invalid.detectedViolations());
        var errors = invalid.examples();
        assertEquals(List.of(TEXT_TOO_LONG, VALUE_OUT_OF_RANGE, VALUE_OUT_OF_RANGE, MANDATORY_CONSTRAINT_FALSE,
                LOCAL_UNIQUE_DUPLICATE, REQUIRED_VALUE_MISSING), errors.stream().map(ValidationDiagnostic::code).toList());
        assertEquals(List.of(List.of(f.slot("code")), List.of(f.slot("count")),
                        List.of(f.slot("point"), new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.AXIS, 1)),
                        List.of(), List.of(f.slot("details"), new DataLocation.Index(2)), List.of(f.slot("code"))),
                errors.stream().map(d -> d.data().path()).toList());
        assertEquals(List.of("i1", "i1", "i1", "i1", "i1", "i2"),
                errors.stream().map(d -> d.data().tid().orElseThrow().value()).toList());
        assertTrue(errors.stream().allMatch(d -> d.rule().range().isPresent()));
        assertEquals(List.of("1", "2"), errors.get(4).arguments());
        assertCoverage(f, invalid, 2);
        assertEquals(f.neutral(true), invalid);
    }

    @ParameterizedTest
    @EnumSource(Version.class)
    void globalOnlyViolationRemainsOutsideCheckedScope(Version version) throws IOException {
        var f = new Fixture(version);
        var report = f.read(version.fixture("global-only.xtf"));
        assertEquals(ValidationReport.Result.PASSED_CHECKED_SCOPE, report.result());
        assertEquals(2, report.inspectedObjects());
        assertEquals(0, report.detectedViolations());
        assertCoverage(f, report, 2);
    }

    @Test
    void changingTheXtf24BasketDomainChangesTheAxisResult() throws IOException {
        var version = Version.XTF24;
        var f = new Fixture(version);
        var far = new String(version.fixture("valid.xtf"), StandardCharsets.UTF_8)
                .replace("Checks.G=Checks.Local", "Checks.G=Checks.Far");
        var report = f.read(far.getBytes(StandardCharsets.UTF_8));
        var diagnostic = report.examples().getFirst();
        assertEquals(List.of(VALUE_OUT_OF_RANGE, VALUE_OUT_OF_RANGE),
                report.examples().stream().map(ValidationDiagnostic::code).toList());
        assertEquals(List.of(f.slot("point"),
                new DataLocation.GeometryComponent(DataLocation.GeometryComponent.Kind.AXIS, 1)), diagnostic.data().path());
        assertEquals(List.of("10.00", "200.00", "300.00"), diagnostic.arguments());
        assertCoverage(f, report, 1);
    }

    @ParameterizedTest
    @EnumSource(Version.class)
    void malformedTrailingInputAndCancellationDoNotReturnReports(Version version) throws IOException {
        var f = new Fixture(version);
        var valid = version.fixture("valid.xtf");
        var corrupt = (new String(valid, StandardCharsets.UTF_8) + "<trailing/>").getBytes(StandardCharsets.UTF_8);
        try (var reader = version.reader(f.transfer, new ByteArrayInputStream(corrupt))) {
            if (version == Version.XTF23) {
                assertThrows(Xtf23Exception.class, () -> LocalValidator.validate(f.plan, reader, OPTIONS, RUN));
            } else {
                assertThrows(Xtf24Exception.class, () -> LocalValidator.validate(f.plan, reader, OPTIONS, RUN));
            }
        }
        try (var reader = version.reader(f.transfer, new ByteArrayInputStream(valid))) {
            assertThrows(OperationCancelledException.class,
                    () -> LocalValidator.validate(f.plan, reader, OPTIONS, () -> true));
        }
    }

    private static void assertCoverage(Fixture f, ValidationReport report, long objects) {
        var coverage = report.coverage();
        assertEquals(List.of(ValidationCoverage.RuleFamily.REFERENTIAL_INTEGRITY, ValidationCoverage.RuleFamily.TOPOLOGY),
                coverage.unavailableFamilies());
        assertEquals(List.of(UncheckedRuleInfo.Reason.OUT_OF_SCOPE_GLOBAL),
                coverage.notChecked().stream().map(UncheckedRuleInfo::reason).toList());
        assertEquals(f.ref("Data", "Item"), coverage.notChecked().getFirst().objectType());
        assertEquals(2, coverage.constraints().size());
        assertTrue(coverage.constraints().stream().allMatch(c -> c.objectType().equals(f.ref("Data", "Item"))
                && c.status() == ValidationCoverage.ConstraintStatus.CHECKED && c.occurrences() == objects));
        assertTrue(coverage.missingRuntimeContext().isEmpty());
        assertTrue(coverage.unsupportedValues().isEmpty());
    }

    private static TransferValue.NumberValue number(String value) {
        return new TransferValue.NumberValue(value, new BigDecimal(value));
    }

    private static final class Fixture {
        private final Version version;
        private final CompiledSchema schema;
        private final TransferPlan transfer;
        private final ValidationPlan plan;

        Fixture(Version version) throws IOException {
            this.version = version;
            schema = compile(new String(version.fixture("Checks.ili"), StandardCharsets.UTF_8));
            transfer = transfer(schema);
            plan = ready(schema, transfer);
        }

        SymbolRef ref(String... relative) {
            var path = new ArrayList<>(List.of("Checks"));
            path.addAll(List.of(relative));
            return schema.queries().findQualifiedName(QualifiedName.of(path.toArray(String[]::new))).getFirst();
        }

        SymbolRef field(String name) {
            return ref("Data", "Item", name);
        }

        DataLocation.Step slot(String name) {
            return new DataLocation.Slot(field(name));
        }

        ValidationReport read(byte[] bytes) {
            var input = new TrackingInput(bytes);
            ValidationReport report;
            var reader = version.reader(transfer, input);
            try (reader) {
                report = LocalValidator.validate(plan, reader, OPTIONS, RUN);
                assertFalse(input.closed);
                assertTrue(reader.readNext(RUN).isEmpty());
            }
            assertThrows(TransferProtocolException.class, () -> reader.readNext(RUN));
            assertFalse(input.closed);
            input.close();
            return report;
        }

        ValidationReport neutral(boolean invalid) {
            var basket = new TransferIdentity.BasketId("b1", TransferIdentity.IdOrigin.SOURCE);
            var bindings = version == Version.XTF24
                    ? List.of(new TransferEvent.DomainBinding(ref("G"), ref("Local"))) : List.<TransferEvent.DomainBinding>of();
            var detail = ref("Data", "Detail");
            var key = ref("Data", "Detail", "key");
            var a = new TransferValue.Structured(detail, Map.of(key, new TransferValue.Text("a")));
            var b = new TransferValue.Structured(detail, Map.of(key, new TransferValue.Text("b")));
            var details = new TransferValue.Repeated(CollectionKind.BAG, List.of(a, invalid ? a : b));
            var point = new TransferGeometry.Coordinate(List.of(number(invalid ? "110.00" : "10.00"), number("20.00")));
            try (var session = LocalValidator.session(plan, OPTIONS)) {
                session.accept(new TransferEvent.StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of()), RUN);
                session.accept(new TransferEvent.StartBasket(basket, ref("Data"), List.of(), TransferEvent.BasketKind.FULL,
                        TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), bindings), RUN);
                session.accept(item(basket, "i1", Map.of(field("code"), new TransferValue.Text(invalid ? "abcd" : "abc"),
                        field("count"), number(invalid ? "12" : "2"), field("active"), new TransferValue.BooleanValue(!invalid),
                        field("point"), point, field("details"), details)), RUN);
                if (invalid) {
                    session.accept(item(basket, "i2", Map.of(field("count"), number("2"),
                            field("active"), new TransferValue.BooleanValue(true))), RUN);
                }
                session.accept(new TransferEvent.EndBasket(basket), RUN);
                session.accept(new TransferEvent.EndTransfer(), RUN);
                return session.finish();
            }
        }

        private TransferEvent.ObjectEvent item(TransferIdentity.BasketId basket, String tid, Map<SymbolRef, TransferValue> fields) {
            var identity = new TransferIdentity(basket, Optional.empty(),
                    Optional.of(new TransferIdentity.TransferId(tid, TransferIdentity.IdOrigin.SOURCE)),
                    Optional.empty(), Optional.empty());
            var object = new TransferObject(identity, new TransferValue.Structured(ref("Data", "Item"), fields));
            return new TransferEvent.ObjectEvent(object, Optional.empty(), TransferEvent.Consistency.COMPLETE);
        }
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;

        TrackingInput(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
