package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.assertMissingTarget;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrsCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;

    CrsCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
    }

    void defaults(TestDatabase db, TestDatabase own, int epsg) throws Exception {
        String model = "DefaultCrs" + version + "_" + epsg;
        Path ili = defaultModel(model, epsg);
        createReference(db, ili, epsg);
        var creation = cli(own, "schemaimport", model, null, ili, CancellationToken.none(),
                "--default-epsg", Integer.toString(epsg));
        assertEquals(0, creation.code(), creation::diagnostics);
        try (var ref = db.connect(); var sql = own.connect()) {
            DbSchemaCatalog.read(ref, db.postgis(), db.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, own.postgis(), own.schema()));
        }
        defaultExchange(db, model, epsg);
        defaultExchange(own, model, epsg);

        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_settings")
                    + " SET setting = '2056' WHERE tag = 'ch.ehi.ili2db.defaultSrsCode'");
        }
        Path output = Files.writeString(work.resolve(model + "-bad-catalog.xtf"), "previous output");
        var failed = cli(db, "export", model, "legacy", output, CancellationToken.none());
        assertNotEquals(0, failed.code(), failed::diagnostics);
        assertTrue(failed.diagnostics().contains("Expected an EPSG:2056"), failed::diagnostics);
        assertTrue(failed.diagnostics().contains("station.location"), failed::diagnostics);
        assertEquals("previous output", Files.readString(output));
    }

    void rejectMissingDefault(TestDatabase db) throws Exception {
        String model = "MissingCrs" + version;
        Path ili = defaultModel(model, 4326);
        var result = cli(db, "schemaimport", model, null, ili, CancellationToken.none(), "--default-epsg", "999999");
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("missing the EPSG:999999 SRS"), result::diagnostics);
        assertMissingTarget(db);
    }

    private Path defaultModel(String model, int epsg) throws Exception {
        String ranges = epsg == 21781 ? "460000.000 .. 870000.000, 45000.000 .. 310000.000"
                : "-180.000 .. 180.000, -90.000 .. 90.000";
        return Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%s;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS Station =
                      Location : COORD %s;
                    END Station;
                  END Data;
                END %s.
                """.formatted(version % 10, model, ranges, model));
    }

    private void createReference(TestDatabase db, Path ili, Integer epsg, String... extra) throws Exception {
        var args = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG"));
        if (epsg != null) args.addAll(List.of("--defaultSrsCode", epsg.toString()));
        args.addAll(List.of(extra));
        args.add(ili.toString());
        reference.run(db, work.toString(), args.toArray(String[]::new));
    }

    private void defaultExchange(TestDatabase db, String model, int epsg) throws Exception {
        String x = epsg == 21781 ? "600100.125" : "7.125";
        String y = epsg == 21781 ? "200200.375" : "46.375";
        mixedExchange(db, model, "default", (tid) ->
                object(model, "Station", tid, field("Location", point(x, y))), (output, tid) ->
                assertPoint(output, tid, "Location", x, y));
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "legacy-default", epsg);
            assertStoredEpsg(db, sql, "station", "location", "own-default", epsg);
        }
    }

    @FunctionalInterface
    private interface ObjectFixture {
        String xml(String tid);
    }

    @FunctionalInterface
    private interface ExportAssertion {
        void verify(Path output, String tid) throws Exception;
    }

    private void mixedExchange(TestDatabase db, String model, String part, ObjectFixture object,
                               ExportAssertion assertion) throws Exception {
        String legacy = "legacy-" + part;
        String own = "own-" + part;
        Path input = delivery(model, "basket-" + legacy, object.xml(legacy));
        reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importTid", "--importBid", input.toString());
        Path output = work.resolve(db.schema() + "-native.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertion.verify(output, legacy);

        input = delivery(model, "basket-" + own, object.xml(own));
        assertCliSuccess(db, "import", model, "own", input);
        output = work.resolve(db.schema() + "-reference.xtf");
        reference.run(db, work.toString(), "--export", "--dataset", "own", "--exportTid", output.toString());
        assertion.verify(output, own);
    }

    private void assertStoredEpsg(TestDatabase db, Connection sql, String table, String field, String tid, int epsg)
            throws Exception {
        String query = " FROM " + db.table(table) + " WHERE t_ili_tid = '" + tid + "'";
        if (db.postgis()) {
            assertEquals(Integer.toString(epsg), scalar(sql, "SELECT ST_SRID(" + field + ")" + query));
        } else {
            try (var stmt = sql.createStatement(); var rows = stmt.executeQuery("SELECT " + field + query)) {
                assertTrue(rows.next());
                byte[] blob = rows.getBytes(1);
                var order = (blob[3] & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                assertEquals(epsg, ByteBuffer.wrap(blob).order(order).getInt(4));
            }
        }
    }

    private Path delivery(String model, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(model + "-" + bid + ".xtf"), text);
    }

    private String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private String point(String x, String y) {
        return version == 23 ? "<COORD><C1>%s</C1><C2>%s</C2></COORD>".formatted(x, y)
                : "<g:coord><g:c1>%s</g:c1><g:c2>%s</g:c2></g:coord>".formatted(x, y);
    }

    private static String field(String name, String value) {
        return "<" + name + ">" + value + "</" + name + ">";
    }

    private void assertPoint(Path output, String tid, String field, String x, String y) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals("basket-" + tid, id(basket, version, "bid"));
        var object = children(basket).getFirst();
        assertEquals(tid, id(object, version, "tid"));
        var position = children(children(child(object, field)).getFirst());
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(position.get(0).getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(position.get(1).getTextContent())));
    }
}
