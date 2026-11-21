package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeometryLayoutCases {
    private final Path work;
    private final int version;
    private final String model;
    private final ReferenceTool reference;

    GeometryLayoutCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        model = "DbGeometryLayout" + version;
        this.reference = reference;
    }

    void singleColumnProfile(TestDatabase db) throws Exception {
        Path ili = modelFile();
        var failed = cli(db, "schemaimport", model, null, ili, CancellationToken.none(), "--create-geom-index");
        assertNotEquals(0, failed.code(), failed::diagnostics);
        assertTrue(failed.diagnostics().contains("multiple GeoPackage geometry columns"), failed::diagnostics);
    }

    void sameTable(TestDatabase old, TestDatabase own) throws Exception {
        Path ili = modelFile();
        reference.run(old, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx", ili.toString());
        var create = cli(own, "schemaimport", model, null, ili, CancellationToken.none(), "--create-geom-index");
        assertEquals(0, create.code(), create::diagnostics);

        Path input = delivery("layout-input", asset("a1", "2600100.125", "-40.125", true)
                + asset("a2", null, "90.25", false));
        reference.run(old, work.toString(), "--import", "--dataset", "layout", "--importTid", "--importBid", input.toString());
        Path newOutput = work.resolve("layout-new.xtf");
        assertCliSuccess(old, "export", model, "layout", newOutput);
        assertTransfer(newOutput, "2600100.125", "-40.125", true, 2);

        assertCliSuccess(own, "import", model, "layout", input);
        Path oldOutput = work.resolve("layout-old.xtf");
        reference.run(own, work.toString(), "--export", "--dataset", "layout", "--exportTid", oldOutput.toString());
        assertTransfer(oldOutput, "2600100.125", "-40.125", true, 2);

        try (var oldSql = old.connect(); var sql = own.connect()) {
            DbSchemaCatalog.read(oldSql, true, old.schema()).assertSameAs(DbSchemaCatalog.read(sql, true, own.schema()));
            assertEquals("3", scalar(sql, "SELECT count(*) FROM pg_indexes WHERE schemaname='" + own.schema()
                    + "' AND tablename='asset' AND indexdef LIKE '%USING gist%'"));
            String key = scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'");
            Path changed = delivery("layout-changed", asset("a1", "2600400.500", null, true));
            assertCliSuccess(own, "update", model, "layout", changed);
            assertEquals(key, scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + own.table("asset") + " WHERE surveyed IS NULL"));
            assertPostgisHit(own, sql, "2600100.125", "0");
            assertPostgisHit(own, sql, "2600400.500", "1");
            reference.run(own, work.toString(), "--export", "--dataset", "layout", "--exportTid", oldOutput.toString());
            assertTransfer(oldOutput, "2600400.500", null, true, 1);
        }
    }

    private Path modelFile() throws Exception {
        var ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var fixture = getClass().getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(fixture, ili);
            }
        }
        return ili;
    }

    private static void assertPostgisHit(TestDatabase db, Connection sql, String x, String expected) throws Exception {
        assertEquals(expected, scalar(sql, "SELECT count(*) FROM " + db.table("asset")
                + " WHERE anchor && ST_MakeEnvelope(" + x + ",1200200," + x + ",1200201,2056)"));
    }

    private Path delivery(String name, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, "layout-basket", objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    private String asset(String tid, String x, String z, boolean trace) {
        String body = x == null ? "" : "<Anchor>" + position(x, "1200200.375", null) + "</Anchor>";
        if (z != null) body += "<Surveyed>" + position("2600200.125", "1200300.375", z) + "</Surveyed>";
        if (trace) {
            String line = version == 23 ? "POLYLINE" : "g:polyline";
            body += "<Trace><" + line + ">" + position("2600600.125", "1200600.375", null)
                    + position("2600620.250", "1200640.500", null) + "</" + line + "></Trace>";
        }
        return version == 23 ? "<%1$s.Data.Asset TID=\"%2$s\">%3$s</%1$s.Data.Asset>".formatted(model, tid, body)
                : "<Asset i:tid=\"%s\">%s</Asset>".formatted(tid, body);
    }

    private String position(String x, String y, String z) {
        return version == 23 ? "<COORD><C1>" + x + "</C1><C2>" + y + "</C2>"
                + (z == null ? "" : "<C3>" + z + "</C3>") + "</COORD>"
                : "<g:coord><g:c1>" + x + "</g:c1><g:c2>" + y + "</g:c2>"
                + (z == null ? "" : "<g:c3>" + z + "</g:c3>") + "</g:coord>";
    }

    private void assertTransfer(Path file, String x, String z, boolean trace, int count) throws Exception {
        var basket = children(readDataSection(file, version)).getFirst();
        var objects = children(basket);
        assertEquals("layout-basket", id(basket, version, "bid"));
        assertEquals(count, objects.size());
        var first = objects.stream().filter(object -> id(object, version, "tid").equals("a1")).findFirst().orElseThrow();
        var anchor = children(children(child(first, "Anchor")).getFirst()).getFirst();
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(anchor.getTextContent())));
        if (z == null) {
            assertNull(child(first, "Surveyed"));
        } else {
            var surveyed = children(children(child(first, "Surveyed")).getFirst());
            assertEquals(0, new BigDecimal(z).compareTo(new BigDecimal(surveyed.get(2).getTextContent())));
        }
        if (trace) assertNotNull(child(first, "Trace"));
        else assertNull(child(first, "Trace"));
    }
}
