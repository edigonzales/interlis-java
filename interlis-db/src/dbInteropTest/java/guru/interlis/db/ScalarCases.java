package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.assertCliInZone;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ScalarCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ScalarCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void wideIntegers(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "WideInteger" + version;
        schemas(expected, own, model, """
                CLASS Asset =
                  WideNo : -9223372036854775808 .. 9223372036854775807;
                  SmallNo : -100 .. 100;
                END Asset;
                """);
        var numbers = List.of("9007199254740993", "-9223372036854775808", "9223372036854775807");
        var values = new ArrayList<>(numbers.stream().map(value -> field("WideNo", value) + field("SmallNo", "13")).toList());
        values.add("");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                for (int i = 0; i < numbers.size(); i++) {
                    assertEquals(numbers.get(i), child(rows.get(i), "WideNo").getTextContent());
                    assertEquals("13", child(rows.get(i), "SmallNo").getTextContent());
                }
                assertNull(child(rows.getLast(), "WideNo"));
            });
            try (var sql = db.connect()) {
                for (int i = 0; i < numbers.size(); i++) {
                    assertEquals(numbers.get(i), stored(db, sql, "wideno", "native", i));
                }
                assertNull(stored(db, sql, "wideno", "native", numbers.size()));
                var broken = data(model, "broken", List.of(values.getFirst(), field("WideNo", "9223372036854775808")));
                assertCliFailure(db, "import", model, "broken", broken, "64-bit integer encoding");
                assertCounts(db, sql, 8);
            }
        }
    }

    void decimals(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarDecimal" + version;
        schemas(expected, own, model, """
                DOMAIN Length = -100.000 .. 100.000 [INTERLIS.m];
                CLASS Asset =
                  PriceValue : -9999999999999999.999 .. 9999999999999999.999;
                  Measure : Length;
                END Asset;
                """);
        var values = List.of(field("PriceValue", "-1.125") + field("Measure", "2.250"), "");
        var high = new BigDecimal("9007199254740993.125");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> {
                assertNumber("-1.125", child(rows.getFirst(), "PriceValue").getTextContent());
                assertNumber("2.250", child(rows.getFirst(), "Measure").getTextContent());
                assertNull(child(rows.getLast(), "PriceValue"));
            });
            try (var sql = db.connect()) {
                assertNumber("-1.125", stored(db, sql, "pricevalue", "native", 0));
                assertNull(stored(db, sql, "pricevalue", "native", 1));
                assertEquals("m", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                        + " WHERE tablename = 'asset' AND columnname = 'measure' AND tag = 'ch.ehi.ili2db.unit'"));
                if (db.postgis()) {
                    assertEquals("19:3", scalar(sql, "SELECT numeric_precision || ':' || numeric_scale FROM information_schema.columns"
                            + " WHERE table_schema = '" + db.schema() + "' AND table_name = 'asset' AND column_name = 'pricevalue'"));
                }
                referenceImport(db, "rounded", data(model, "rounded", List.of(field("PriceValue", high.toPlainString()))));
                assertNotEquals(0, high.compareTo(new BigDecimal(stored(db, sql, "pricevalue", "rounded", 0))));
                assertCliSuccess(db, "import", model, "precise", data(model, "precise", List.of(field("PriceValue", high.toPlainString()))));
                var output = work.resolve("out.xtf");
                assertCliSuccess(db, "export", model, "precise", output);
                String exact = db.postgis() ? high.toPlainString() : BigDecimal.valueOf(high.doubleValue()).toPlainString();
                assertNumber(exact, child(rows(output, "precise", 1).getFirst(), "PriceValue").getTextContent());
            }
        }
    }

    private static void assertNumber(String expected, String actual) {
        assertEquals(new BigDecimal(expected).stripTrailingZeros(), new BigDecimal(actual).stripTrailingZeros());
    }

    void calendarDate(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarCalendar" + version;
        String dateDomain = version == 23 ? "" : "DOMAIN DayValue = DATE;";
        schemas(expected, own, model, """
                %s
                CLASS Asset = CalendarDay : %s; END Asset;
                """.formatted(dateDomain, version == 23 ? "INTERLIS.XMLDate" : "DayValue"));
        var dates = List.of("2024-02-29", "1999-12-31");
        var values = new ArrayList<>(dates.stream().map(value -> field("CalendarDay", value)).toList());
        values.add("");
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, rows -> assertTextValues(rows, "CalendarDay", "2024-02-29", "1999-12-31", null));
            try (var sql = db.connect()) {
                assertEquals("2024-02-29", stored(db, sql, "calendarday", "native", 0));
                assertNull(stored(db, sql, "calendarday", "native", 2));
                execute(sql, "UPDATE " + db.table("asset") + " SET calendarday = "
                        + (db.postgis() ? "'infinity'::date" : "'2024-02-30'") + " WHERE t_ili_tid = 'native-0'");
                var output = work.resolve("out.xtf");
                byte[] previous = Files.readAllBytes(output);
                assertCliFailure(db, "export", model, "native", output, "Invalid SQL date");
                assertArrayEquals(previous, Files.readAllBytes(output));
            }
        }
    }

    void localTime(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarClock" + version;
        schemas(expected, own, model, "CLASS Asset = ReadingTime : "
                + (version == 23 ? "INTERLIS.XMLTime" : "TIMEOFDAY") + "; END Asset;");
        var values = List.of(field("ReadingTime", "00:00:00.000"), field("ReadingTime", "23:59:58.125"), "");
        CheckRows check = rows -> assertTextValues(rows, "ReadingTime", "00:00:00.000", "23:59:58.125", null);
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, check);
            differentZones(db, model, values, check);
            try (var sql = db.connect()) {
                assertEquals("23:59:58.125", stored(db, sql, "readingtime", "zoned", 1));
                assertNull(stored(db, sql, "readingtime", "native", 2));
            }
        }
    }

    void localTimestamp(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarTimestamp" + version;
        schemas(expected, own, model, "CLASS Asset = ReadingStamp : "
                + (version == 23 ? "INTERLIS.XMLDateTime" : "DATETIME") + "; END Asset;");
        String gap = "2024-03-10T02:30:15.125";
        String spring = "2024-03-31T01:59:58.375";
        var values = List.of(field("ReadingStamp", gap), field("ReadingStamp", spring), "");
        CheckRows check = rows -> assertTextValues(rows, "ReadingStamp", gap, spring, null);
        for (var db : List.of(expected, own)) {
            mixed(db, model, values, check);
            differentZones(db, model, values, check);
            try (var sql = db.connect()) {
                String stored = db.postgis() ? "readingstamp::text" : "readingstamp";
                assertEquals(db.postgis() ? gap.replace('T', ' ') : gap + "Z", stored(db, sql, stored, "zoned", 0));
                assertEquals(db.postgis() ? spring.replace('T', ' ') : spring + "Z", stored(db, sql, stored, "native", 1));
                assertNull(stored(db, sql, stored, "legacy", 2));
            }
        }
    }

    void formatted(TestDatabase expected, TestDatabase own) throws Exception {
        String model = "ScalarFormatted" + version;
        schemas(expected, own, model, """
                STRUCTURE Digits = Numeral : 0..9999; END Digits;
                DOMAIN Ticket = FORMAT BASED ON Digits (Numeral/4) "0000" .. "9999";
                       CalRange EXTENDS INTERLIS.XMLDate = "1900-01-01" .. "2100-12-31";
                CLASS Asset =
                  TicketCode : Ticket;
                  RestrictedDay : CalRange;
                  NameCode : NAME;
                  UriCode : URI;
                END Asset;
                """);
        String uri = "https://example.org/A%20B?x=1&y=2";
        var values = List.of(field("TicketCode", "0012") + field("RestrictedDay", "2024-02-29")
                + field("NameCode", "A_1") + field("UriCode", uri.replace("&", "&amp;")), "");
        for (var db : List.of(expected, own)) {
            try (var sql = db.connect()) {
                execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN ticketcode TO stored_ticket");
                execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname = 'stored_ticket' WHERE sqlname = 'ticketcode'");
                execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                        + " SET columnname = 'stored_ticket' WHERE columnname = 'ticketcode'");
                mixed(db, model, values, rows -> {
                    assertTextValues(rows, "TicketCode", "0012", null);
                    assertTextValues(rows, "RestrictedDay", "2024-02-29", null);
                    assertTextValues(rows, "NameCode", "A_1", null);
                    assertTextValues(rows, "UriCode", uri, null);
                });
                assertEquals("0012", stored(db, sql, "stored_ticket", "native", 0));
                assertNull(stored(db, sql, "stored_ticket", "native", 1));
            }
        }
    }

    private void differentZones(TestDatabase db, String model, List<String> values, CheckRows check) throws Exception {
        Path output = work.resolve("zoned-out.xtf");
        assertCliInZone(db, "export", model, "legacy", output, "Pacific/Auckland");
        check.check(rows(output, "legacy", values.size()));
        assertCliInZone(db, "import", model, "zoned", data(model, "zoned", values), "America/New_York");
        referenceExport(db, "zoned", output);
        check.check(rows(output, "zoned", values.size()));
    }

    private static void assertTextValues(List<Element> rows, String attribute, String... values) {
        assertEquals(values.length, rows.size());
        for (int i = 0; i < values.length; i++) {
            var value = child(rows.get(i), attribute);
            assertEquals(values[i], value == null ? null : value.getTextContent(), attribute + " in object " + i);
        }
    }

    @FunctionalInterface
    private interface CheckRows {
        void check(List<Element> rows) throws Exception;
    }

    private void mixed(TestDatabase db, String model, List<String> fields, CheckRows check) throws Exception {
        Path output = work.resolve("out.xtf");
        referenceImport(db, "legacy", data(model, "legacy", fields));
        assertCliSuccess(db, "export", model, "legacy", output);
        check.check(rows(output, "legacy", fields.size()));
        assertCliSuccess(db, "import", model, "native", data(model, "native", fields));
        referenceExport(db, "native", output);
        check.check(rows(output, "native", fields.size()));
    }

    private void schemas(TestDatabase expected, TestDatabase own, String model, String declarations) throws Exception {
        Path ili = source(model, declarations);
        referenceSchema(expected, model);
        assertCliSuccess(own, "schemaimport", model, null, ili);
        sameCatalog(expected, own);
    }

    private Path source(String model, String declarations) throws Exception {
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
    }

    private void referenceSchema(TestDatabase db, String model, String... extra) throws Exception {
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model));
        options.addAll(List.of(extra));
        reference.run(db, work.toString(), options.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        Files.deleteIfExists(output);
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }

    private static void sameCatalog(TestDatabase expected, TestDatabase own) throws Exception {
        try (var left = expected.connect(); var right = own.connect()) {
            DbSchemaCatalog.read(left, expected.postgis(), expected.schema())
                    .assertSameAs(DbSchemaCatalog.read(right, own.postgis(), own.schema()));
        }
    }

    private Path data(String model, String prefix, List<String> values) throws Exception {
        String tag = version == 23 ? model + ".Data.Asset" : "Asset";
        String attribute = version == 23 ? " TID" : " i:tid";
        String objects = IntStream.range(0, values.size()).mapToObj(i -> "<" + tag + attribute + "=\""
                + prefix + "-" + i + "\">" + values.get(i) + "</" + tag + ">").collect(Collectors.joining());
        return Files.writeString(work.resolve(prefix + ".xtf"), referenceDatasetFixture(model, version, prefix + "-basket", objects));
    }

    private List<Element> rows(Path output, String prefix, int count) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(prefix + "-basket", id(baskets.getFirst(), version, "bid"));
        var byTid = children(baskets.getFirst()).stream().collect(Collectors.toMap(row -> id(row, version, "tid"), row -> row));
        assertEquals(count, byTid.size());
        return IntStream.range(0, count).mapToObj(i -> byTid.get(prefix + "-" + i)).toList();
    }

    private static String field(String name, String content) {
        return "<" + name + ">" + content + "</" + name + ">";
    }

    private static String stored(TestDatabase db, Connection sql, String expression, String prefix, int row) throws Exception {
        return scalar(sql, "SELECT " + expression + " FROM " + db.table("asset")
                + " WHERE t_ili_tid = '" + prefix + "-" + row + "'");
    }

    private static void assertCounts(TestDatabase db, Connection sql, int objects) throws Exception {
        assertEquals(Integer.toString(objects), scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
    }
}
