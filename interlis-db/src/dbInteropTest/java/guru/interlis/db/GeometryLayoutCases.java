package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
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

    void sameTable(TestDatabase old, TestDatabase own) throws Exception {
        create(old, own, false);
        mixedTransfers(old, own);
        try (var sql = own.connect()) {
            assertEquals("3", scalar(sql, "SELECT count(*) FROM pg_indexes WHERE schemaname='" + own.schema()
                    + "' AND tablename='asset' AND indexdef LIKE '%USING gist%'"));
            String key = scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'");
            Path changed = changed();
            assertCliSuccess(own, "update", model, "layout", changed);
            assertEquals(key, scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + own.table("asset") + " WHERE surveyed IS NULL"));
            assertPostgisHit(own, sql, "2600100.125", "0");
            assertPostgisHit(own, sql, "2600400.500", "1");
            Path output = work.resolve("layout-updated.xtf");
            reference.run(own, work.toString(), "--export", "--dataset", "layout", "--exportTid", output.toString());
            assertTransfer(output, "2600400.500", null, true, 1);
        }
    }

    void split(TestDatabase old, TestDatabase own) throws Exception {
        create(old, own, true);
        mixedTransfers(old, own);
        Path changed = changed();
        try (var sql = own.connect()) {
            if (!own.postgis()) GeoPackageRtree.register(sql);
            String id = scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'");
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + own.table("asset_trace") + " WHERE trace IS NULL"));
            assertEquals("2", fragmentRows(own, sql, "asset_trace"));
            assertEquals("2", fragmentRows(own, sql, "asset_surveyed"));
            assertCliSuccess(own, "update", model, "layout", changed);
            assertEquals(id, scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'"));
            assertEquals("1", fragmentRows(own, sql, "asset_trace"));
            assertEquals("1", fragmentRows(own, sql, "asset_surveyed"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + own.table("asset_surveyed") + " WHERE surveyed IS NULL"));
            String basket = scalar(sql, "SELECT t_basket FROM " + own.table("asset") + " WHERE t_id=" + id);
            execute(sql, "UPDATE " + own.table("asset_trace") + " SET t_basket=-1 WHERE t_id=" + id);
            Path output = Files.writeString(work.resolve("layout-corrupt.xtf"), "previous output");
            var failed = cli(own, "export", model, "layout", output, CancellationToken.none());
            assertNotEquals(0, failed.code(), failed::diagnostics);
            assertTrue(failed.diagnostics().contains("Secondary geometry belongs to a different basket"), failed::diagnostics);
            assertEquals("previous output", Files.readString(output));
            execute(sql, "UPDATE " + own.table("asset_trace") + " SET t_basket=" + basket + " WHERE t_id=" + id);

            assertCliSuccess(own, "replace", model, "layout", changed);
            assertNotEquals(id, scalar(sql, "SELECT t_id FROM " + own.table("asset") + " WHERE t_ili_tid='a1'"));
            assertEquals("1", fragmentRows(own, sql, "asset_trace"));
            assertCliSuccess(own, "delete", model, "layout", null);
            assertEquals("0", fragmentRows(own, sql, "asset_trace"));
            assertEquals("0", fragmentRows(own, sql, "asset_surveyed"));
        }

        reference.run(old, work.toString(), "--update", "--dataset", "layout", "--importTid", "--importBid", changed.toString());
        Path updated = work.resolve("split-legacy-update.xtf");
        assertCliSuccess(old, "export", model, "layout", updated);
        assertTransfer(updated, "2600400.500", null, true, 1);
        if (!old.postgis()) {
            try (var sql = old.connect()) {
                assertEquals("2", fragmentRows(old, sql, "asset_surveyed"));
                assertCliSuccess(old, "delete", model, "layout", null);
                assertEquals("0", fragmentRows(old, sql, "asset_surveyed"));
            }
        }
    }

    void wideGeoPackage(TestDatabase old, TestDatabase own) throws Exception {
        create(old, own, false);
        mixedTransfers(old, own);
        Path changed = changed();
        try (var sql = own.connect()) {
            assertEquals("3", scalar(sql, "SELECT count(*) FROM gpkg_geometry_columns WHERE table_name='asset'"));
            assertEquals("2460000.125", scalar(sql, "SELECT min_x FROM gpkg_contents WHERE table_name='asset'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM rtree_asset_anchor"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM rtree_asset_surveyed"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM rtree_asset_trace"));
            assertEquals("1", geoPackageHits(sql, "2600100.125"));

            String trigger = scalar(sql, "SELECT sql FROM sqlite_schema WHERE name='rtree_asset_surveyed_update2'");
            execute(sql, "DROP TRIGGER rtree_asset_surveyed_update2");
            var failure = cli(own, "update", model, "layout", changed, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("asset.surveyed: missing index maintenance trigger"),
                    failure::diagnostics);
            assertEquals("2", scalar(sql, "SELECT count(*) FROM asset"));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM rtree_asset_surveyed"));
            assertEquals("1", geoPackageHits(sql, "2600100.125"));

            execute(sql, trigger);
            assertCliSuccess(own, "update", model, "layout", changed);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM rtree_asset_surveyed"));
            assertEquals("0", geoPackageHits(sql, "2600100.125"));
            assertEquals("1", geoPackageHits(sql, "2600400.500"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM rtree_asset_trace"));
            var output = work.resolve("wide-updated.xtf");
            reference.run(own, work.toString(), "--export", "--dataset", "layout", "--exportTid", output.toString());
            assertTransfer(output, "2600400.500", null, true, 1);
        }
    }

    void hierarchy(TestDatabase old, TestDatabase own, boolean split) throws Exception {
        create(old, own, split);
        Path input = delivery("hierarchy", recorder("rec", "2601000.125", "-81.125", true) + beacon("beacon"));
        reference.run(old, work.toString(), "--import", "--dataset", "layout", "--importTid", "--importBid", input.toString());
        var oldResult = work.resolve("hierarchy-native.xtf");
        assertCliSuccess(old, "export", model, "layout", oldResult);
        assertHierarchy(oldResult, 2, "-81.125", true);

        assertCliSuccess(own, "import", model, "layout", input);
        var ownResult = work.resolve("hierarchy-legacy.xtf");
        if (split) {
            // The reference exporter reads Track twice from a split hierarchy, even in its own schema.
            reference.expectFailure(old, work.toString(), "max one POLYLINE value allowed (Track)",
                    "--export", "--dataset", "layout", "--exportTid", work.resolve("hierarchy-broken-reference.xtf").toString());
            var legacy = delivery("hierarchy-legacy-input", "legacy-basket", recorder("rec", "2601000.125", "-81.125", true)
                    + beacon("beacon"));
            reference.run(own, work.toString(), "--import", "--dataset", "legacy", "--importTid", "--importBid", legacy.toString());
            assertCliSuccess(own, "export", model, "legacy", ownResult);
            assertCliSuccess(own, "delete", model, "legacy", null);
        } else {
            reference.run(own, work.toString(), "--export", "--dataset", "layout", "--exportTid", ownResult.toString());
        }
        assertHierarchy(ownResult, 2, "-81.125", true);

        Path other = delivery("other", "other-basket", beacon("other"));
        assertCliSuccess(own, "import", model, "other", other);
        Path update = delivery("hierarchy-update", recorder("rec", "2601400.125", "11.750", false));
        try (var sql = own.connect()) {
            String row = scalar(sql, "SELECT t_id FROM " + own.table("device") + " WHERE t_ili_tid='rec'");
            String trackTable = split ? "recorder_track" : "recorder";
            String join = " FROM " + own.table("device") + " d JOIN " + own.table(trackTable)
                    + " r ON r.t_id=d.t_id AND r.t_basket=d.t_basket";
            assertEquals("1", scalar(sql, "SELECT count(*)" + join + " WHERE d.t_type='recorder' AND r.track IS NOT NULL"));
            assertEquals("1", trackHits(own, sql, trackTable, "2601000.125"));
            assertCliSuccess(own, "update", model, "layout", update);
            assertEquals(row, scalar(sql, "SELECT t_id FROM " + own.table("device") + " WHERE t_ili_tid='rec'"));
            assertEquals("1", fragmentRows(own, sql, "beacon"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + own.table("recorder") + " WHERE peg IS NULL"));
            assertEquals("0", trackHits(own, sql, trackTable, "2601000.125"));
            assertEquals("1", trackHits(own, sql, trackTable, "2601400.125"));

            if (split) {
                assertForeignBasketRejected(own, sql, row, update);
            }
            assertCliSuccess(own, "replace", model, "layout", update);
            assertNotEquals(row, scalar(sql, "SELECT t_id FROM " + own.table("device") + " WHERE t_ili_tid='rec'"));
            assertEquals("1", scalar(sql, "SELECT count(*)" + join + " WHERE d.t_type='recorder' AND r.track IS NOT NULL"));
            assertCliSuccess(own, "export", model, "layout", ownResult);
            assertHierarchy(ownResult, 1, "11.750", false);
            assertCliSuccess(own, "delete", model, "layout", null);
            assertEquals("0", fragmentRows(own, sql, trackTable));
            assertEquals("0", trackHits(own, sql, trackTable, "2601400.125"));
            assertEquals("other", scalar(sql, "SELECT t_ili_tid FROM " + own.table("device")));
            assertEquals("1", fragmentRows(own, sql, "beacon"));
        }
    }

    private void assertForeignBasketRejected(TestDatabase db, Connection sql, String row, Path replacement)
            throws Exception {
        if (!db.postgis()) GeoPackageRtree.register(sql);
        String device = db.table("device");
        String ownBasket = scalar(sql, "SELECT t_basket FROM " + device + " WHERE t_id=" + row);
        String otherBasket = scalar(sql, "SELECT t_basket FROM " + device + " WHERE t_ili_tid='other'");
        String setBasket = "UPDATE " + db.table("recorder_track") + " SET t_basket=";
        execute(sql, setBasket + otherBasket + " WHERE t_id=" + row);
        for (String dataset : List.of("layout", "other")) {
            var failure = cli(db, "delete", model, dataset, null, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("basket-mismatched secondary geometry"), failure::diagnostics);
        }
        var failedReplace = cli(db, "replace", model, "layout", replacement, CancellationToken.none());
        assertNotEquals(0, failedReplace.code(), failedReplace::diagnostics);
        assertTrue(failedReplace.diagnostics().contains("basket-mismatched secondary geometry"), failedReplace::diagnostics);
        assertEquals("2", fragmentRows(db, sql, "device"));
        assertEquals("1", trackHits(db, sql, "recorder_track", "2601400.125"));
        execute(sql, setBasket + ownBasket + " WHERE t_id=" + row);
    }

    private static String trackHits(TestDatabase db, Connection sql, String table, String x) throws Exception {
        String query = db.postgis() ? "SELECT count(*) FROM " + db.table(table)
                + " WHERE track && ST_MakeEnvelope(" + x + ",1201000," + x + ",1201001,2056)"
                : "SELECT count(*) FROM rtree_" + table + "_track WHERE minx<=" + x + " AND maxx>=" + x;
        return scalar(sql, query);
    }

    private void assertHierarchy(Path output, int count, String z, boolean peg) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        var objects = children(basket);
        assertEquals(count, objects.size());
        var recorder = objects.stream().filter(object -> id(object, version, "tid").equals("rec")).findFirst().orElseThrow();
        assertTrue(recorder.getLocalName().endsWith("Recorder"));
        var origin = children(children(child(recorder, "Origin")).getFirst());
        assertEquals(0, new BigDecimal(z).compareTo(new BigDecimal(origin.get(2).getTextContent())));
        assertEquals(2, child(recorder, "Track").getElementsByTagNameNS("*", version == 23 ? "C3" : "c3").getLength());
        if (peg) assertNotNull(child(recorder, "Peg"));
        else assertNull(child(recorder, "Peg"));
        if (count == 2) {
            var beacon = objects.stream().filter(object -> id(object, version, "tid").equals("beacon")).findFirst().orElseThrow();
            assertTrue(beacon.getLocalName().endsWith("Beacon"));
            assertNull(child(beacon, "Track"));
            assertNotNull(child(beacon, "Origin"));
            assertNotNull(child(beacon, "Signal"));
        }
    }

    private String recorder(String tid, String trackStart, String originZ, boolean peg) {
        String origin = "<Origin>" + position("2600700.125", "1200700.375", originZ) + "</Origin>";
        String pegField = peg ? "<Peg>" + position("2600800.125", "1200800.375", null) + "</Peg>" : "";
        String line = version == 23 ? "POLYLINE" : "g:polyline";
        String track = "<Track><" + line + ">" + position(trackStart, "1201000.375", "100")
                + position("2601100.375", "1201020.500", "-32") + "</" + line + "></Track>";
        return object("Recorder", tid, origin + pegField + track);
    }

    private String beacon(String tid) {
        return object("Beacon", tid, "<Origin>" + position("2600900.125", "1200900.375", "14.250")
                + "</Origin><Signal>" + position("2600910.125", "1200910.375", null) + "</Signal>");
    }

    private static String geoPackageHits(Connection sql, String x) throws Exception {
        return scalar(sql, "SELECT count(*) FROM rtree_asset_anchor WHERE minx <= " + x + " AND maxx >= " + x);
    }

    private static String fragmentRows(TestDatabase db, Connection sql, String table) throws Exception {
        return scalar(sql, "SELECT count(*) FROM " + db.table(table));
    }

    private Path changed() throws Exception {
        return delivery("layout-changed", asset("a1", "2600400.500", null, true));
    }

    private void create(TestDatabase old, TestDatabase own, boolean split) throws Exception {
        Path ili = modelFile();
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx"));
        var nativeOptions = new ArrayList<>(List.of("--create-geom-index"));
        if (split && old.postgis()) {
            options.add("--oneGeomPerTable");
            nativeOptions.add("--one-geom-per-table");
        } else if (!split && !old.postgis()) {
            options.add("--gpkgMultiGeomPerTable");
            nativeOptions.add("--gpkg-multi-geom-per-table");
        }
        options.add(ili.toString());
        reference.run(old, work.toString(), options.toArray(String[]::new));
        var create = cli(own, "schemaimport", model, null, ili, CancellationToken.none(), nativeOptions.toArray(String[]::new));
        assertEquals(0, create.code(), create::diagnostics);
        try (var oldSql = old.connect(); var sql = own.connect()) {
            var expected = DbSchemaCatalog.read(oldSql, old.postgis(), old.schema());
            if (!old.postgis()) {
                expected = expected.expectGeoPackageGeometryExtension(split ? "asset_trace" : "asset", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension(split ? "recorder_track" : "recorder", "track", "COMPOUNDCURVE");
            }
            expected.assertSameAs(DbSchemaCatalog.read(sql, own.postgis(), own.schema()));
        }
    }

    private void mixedTransfers(TestDatabase old, TestDatabase own) throws Exception {
        Path input = delivery("layout-input", asset("a1", "2600100.125", "-40.125", true)
                + asset("a2", null, "90.25", false));
        reference.run(old, work.toString(), "--import", "--dataset", "layout", "--importTid", "--importBid", input.toString());
        Path newOutput = work.resolve(own.schema() + "-new.xtf");
        assertCliSuccess(old, "export", model, "layout", newOutput);
        assertTransfer(newOutput, "2600100.125", "-40.125", true, 2);

        assertCliSuccess(own, "import", model, "layout", input);
        Path oldOutput = work.resolve(own.schema() + "-old.xtf");
        reference.run(own, work.toString(), "--export", "--dataset", "layout", "--exportTid", oldOutput.toString());
        assertTransfer(oldOutput, "2600100.125", "-40.125", true, 2);

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
        return delivery(name, "layout-basket", objects);
    }

    private Path delivery(String name, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
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
        return object("Asset", tid, body);
    }

    private String object(String type, String tid, String body) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, body)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, body);
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
