package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.CompiledSchema;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SymbolRef;
import guru.interlis.transfer.TransferEvent;
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
import static guru.interlis.validation.ValidationPlannerTest.compile;
import static guru.interlis.validation.ValidationPlannerTest.ready;
import static guru.interlis.validation.ValidationPlannerTest.transfer;
import static org.junit.jupiter.api.Assertions.*;

class ScalarRuleTest {
    private static final String HEADER = """
            INTERLIS 2.4;
            MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
            """;

    @Test
    void countsCanonicalStartersIncludingMarksWithZeroCombiningClass() {
        var fixture = new Fixture(HEADER + """
                CHARSET "UTF-8";
                TOPIC T = CLASS Item = value : MANDATORY TEXT*1; END Item; END T;
                END Checks.
                """);
        for (var text : List.of("é", "e\u0301", "🥳", "C\u0302\u0327", "\u034F", "ﬃ")) {
            assertEquals(List.of(), codes(fixture.validate(Map.of("value", text(text)))), text);
        }
        for (var text : List.of("ab", "가", "e\u034F", "❤\uFE0F")) {
            assertEquals(List.of(TEXT_TOO_LONG), codes(fixture.validate(Map.of("value", text(text)))), text);
        }
        assertEquals(List.of(REQUIRED_VALUE_UNDEFINED), codes(fixture.validate(Map.of("value", text("\u0301")))));
    }

    @Test
    void enforcesTextControlsAndVersionSpecificNamesAndUriRules() {
        var fixture = new Fixture(HEADER + """
                CHARSET "UTF-8";
                TOPIC T =
                  CLASS Item = single : TEXT; multi : MTEXT; name : NAME; uri : URI; END Item;
                END T;
                END Checks.
                """);
        assertEquals(List.of(TEXT_CHARACTER_NOT_ALLOWED, INVALID_NAME, INVALID_URI), codes(fixture.validate(Map.of(
                "single", text("a\tb"), "multi", text("a\tb\r\n"), "name", text("XMLNS"), "uri", text("https://a/%zz")))));
        assertEquals(List.of(INVALID_NAME), codes(fixture.validate(Map.of("name", text("_start")))));
        assertEquals(List.of(), codes(fixture.validate(Map.of(
                "name", text("Xmlns_1"), "uri", text("mailto:a@example.org")))));
        assertEquals(List.of(INVALID_URI), codes(fixture.validate(Map.of("uri", text("/relative")))));

        var legacy = new Fixture(HEADER.replace("2.4", "2.3") + """
                TOPIC T = CLASS Item = name : NAME; value : TEXT*1; END Item; END T;
                END Checks.
                """);
        assertEquals(List.of(), codes(legacy.validate(Map.of("name", text("XMLNS"), "value", text("é")))));
        assertEquals(List.of(TEXT_TOO_LONG), codes(legacy.validate(Map.of("value", text("e\u034F")))));
    }

    @Test
    void reportsRepertoireCoverageAndChecksExplicitModelCharset() {
        var ascii = new Fixture(HEADER + """
                CHARSET "US-ASCII";
                TOPIC T = CLASS Item = value : TEXT*4; END Item; END T;
                END Checks.
                """);
        var outsideDeclared = ascii.validate(Map.of("value", text("é")));
        assertEquals(List.of(), codes(outsideDeclared));
        assertEquals(List.of(new ValidationCoverage.RuleCount(ascii.item, ascii.field("value"),
                ValidationCoverage.RuleFamily.VALUE_RANGE, 1)), outsideDeclared.coverage().unsupportedValues());
        var defaultCharset = new Fixture(HEADER + """
                TOPIC T = CLASS Item = value : TEXT*4; END Item; END T;
                END Checks.
                """);
        var outsideAnnex = defaultCharset.validate(Map.of("value", text("🥳")));
        assertEquals(List.of(), codes(outsideAnnex));
        assertEquals(List.of(new ValidationCoverage.RuleCount(defaultCharset.item, defaultCharset.field("value"),
                ValidationCoverage.RuleFamily.VALUE_RANGE, 1)), outsideAnnex.coverage().unsupportedValues());
    }

    @Test
    void checksExactInclusiveRangesAndPermitsHigherTransferPrecision() {
        var fixture = new Fixture(HEADER + """
                DOMAIN Wide = -100.00 .. 200.00 [INTERLIS.m];
                Narrow EXTENDS Wide = -10.50 .. 100.25 [INTERLIS.m];
                DOMAIN FloatRange = 0.10E-2 .. 0.99E2;
                TOPIC T = CLASS Item = value : Narrow; scaled : FloatRange; END Item; END T;
                END Checks.
                """);
        for (var value : List.of("-10.50", "100.25", "1.0", "1.00", "0", "-0", "0.1E1")) {
            assertEquals(List.of(), codes(fixture.validate(Map.of("value", number(value)))), value);
        }
        for (var value : List.of("-10.50001", "100.25001", "1E900")) {
            assertEquals(List.of(VALUE_OUT_OF_RANGE), codes(fixture.validate(Map.of("value", number(value)))), value);
        }
        assertEquals(List.of(), codes(fixture.validate(Map.of("value", number("0.0001")))));
        assertThrows(ValidationLimitException.class, () -> fixture.validate(Map.of("value", number("1E10001"))));
        assertEquals(List.of(), codes(fixture.validate(Map.of("scaled", number("0.11E-1")))));
        assertEquals(List.of(), codes(fixture.validate(Map.of("scaled", number("0.111E-1")))));
    }

    @Test
    void distinguishesEnumerationLeavesTreeNodesAndAlignment() {
        var fixture = new Fixture(HEADER + """
                DOMAIN Branches = (root(a, b), leaf) ORDERED;
                More EXTENDS Branches = (root(c)) ORDERED;
                Tree = ALL OF More;
                TOPIC T = CLASS Item = leaf : More; tree : Tree; aligned : HALIGNMENT; END Item; END T;
                END Checks.
                """);
        assertEquals(List.of(), codes(fixture.validate(Map.of(
                "leaf", enumeration("root", "c"), "tree", enumeration("root"), "aligned", enumeration("Left")))));
        assertEquals(List.of(ENUM_VALUE_NOT_ALLOWED, ENUM_VALUE_NOT_ALLOWED, ENUM_VALUE_NOT_ALLOWED),
                codes(fixture.validate(Map.of("leaf", enumeration("root"), "tree", enumeration("OTHERS"),
                        "aligned", enumeration("Top")))));
        assertEquals(List.of(ENUM_VALUE_NOT_ALLOWED),
                codes(fixture.validate(Map.of("leaf", enumeration("root.a")))));
    }

    @Test
    void checksStandardCalendarsAndLeavesCustomFormatUnchecked() {
        var fixture = new Fixture(HEADER + """
                STRUCTURE Digits = n : 0..99; END Digits;
                DOMAIN Code = FORMAT BASED ON Digits (n/2) "10".."20";
                TOPIC T =
                  CLASS Item =
                    day : DATE;
                    time : TIMEOFDAY;
                    stamp : DATETIME;
                    window : FORMAT INTERLIS.XMLDate "2000-01-01".."2000-12-31";
                    custom : Code;
                  END Item;
                END T;
                END Checks.
                """);
        var pass = fixture.validate(Map.of("day", text("2000-02-29"), "time", text("23:59:59.999"),
                "stamp", text("2999-12-31T00:00:00"), "window", text("2000-01-01"), "custom", text("bogus")));
        assertEquals(List.of(), codes(pass));
        assertEquals(List.of(new ValidationCoverage.RuleCount(fixture.item, fixture.field("custom"),
                ValidationCoverage.RuleFamily.VALUE_RANGE, 1)), pass.coverage().unsupportedValues());
        var fail = fixture.validate(Map.of("day", text("1900-02-29"), "time", text("24:00:00"),
                "stamp", text("3000-01-01T00:00:00"), "window", text("2001-01-01")));
        assertEquals(List.of(INVALID_TEMPORAL_VALUE, INVALID_TEMPORAL_VALUE, INVALID_TEMPORAL_VALUE, VALUE_OUT_OF_RANGE),
                codes(fail));
    }

    @Test
    void acceptsLegacyUnpaddedCalendarComponentsAndChecksLeapYears() {
        var fixture = new Fixture(HEADER.replace("2.4", "2.3") + """
                TOPIC T =
                  CLASS Item = day : INTERLIS.XMLDate; time : INTERLIS.XMLTime; END Item;
                END T;
                END Checks.
                """);
        var pass = fixture.validate(Map.of("day", text("2000-2-29"), "time", text("09:1:2.005")));
        assertEquals(List.of(), codes(pass));
        assertTrue(pass.coverage().unsupportedValues().isEmpty());
        assertEquals(List.of(INVALID_TEMPORAL_VALUE),
                codes(fixture.validate(Map.of("day", text("2100-2-29")))));
    }

    private static TransferValue.Text text(String value) {
        return new TransferValue.Text(value);
    }

    private static TransferValue.NumberValue number(String value) {
        return new TransferValue.NumberValue(value, new BigDecimal(value));
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
        private final SymbolRef topic;

        Fixture(String source) {
            schema = compile(source);
            plan = ready(schema, transfer(schema));
            topic = schema.queries().findQualifiedName(QualifiedName.of("Checks", "T")).getFirst();
            item = schema.queries().findQualifiedName(QualifiedName.of("Checks", "T", "Item")).getFirst();
        }

        SymbolRef field(String name) {
            return schema.queries().findQualifiedName(QualifiedName.of("Checks", "T", "Item", name)).getFirst();
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
                session.accept(new TransferEvent.StartBasket(basket, topic, List.of(), TransferEvent.BasketKind.FULL,
                        TransferEvent.Consistency.COMPLETE, Optional.empty(), Optional.empty(), Optional.empty(), List.of()), token);
                session.accept(new TransferEvent.ObjectEvent(object, Optional.empty(), TransferEvent.Consistency.COMPLETE), token);
                session.accept(new TransferEvent.EndBasket(basket), token);
                session.accept(new TransferEvent.EndTransfer(), token);
                return session.finish();
            }
        }
    }
}
