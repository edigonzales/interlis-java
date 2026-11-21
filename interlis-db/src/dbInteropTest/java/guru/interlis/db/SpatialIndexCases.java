package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpatialIndexCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;

    SpatialIndexCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
    }

    void points(TestDatabase db) throws Exception {
        String model = "DbMutGeom" + version;
        createReferenceSchema(db, model);
        Path start = delivery("indexed-points-original", model, "points",
                feature(model, "GeoLeaf", "moved", "2600100.125")
                + feature(model, "GeoLeaf", "cleared", "2600200.250")
                + feature(model, "GeoLeaf", "dropped", "2600300.375"));
        Path legacyUpdate = delivery("indexed-points-legacy", model, "points",
                feature(model, "GeoLeaf", "moved", "2600400.500")
                + feature(model, "GeoLeaf", "cleared", "2600200.250")
                + feature(model, "GeoLeaf", "dropped", "2600300.375"));
        Path update = delivery("indexed-points-update", model, "points",
                feature(model, "GeoLeaf", "moved", "2600500.625") + feature(model, "GeoLeaf", "cleared", null));
        Path replacement = delivery("indexed-points-replace", model, "points",
                feature(model, "GeoLeaf", "moved", "2600600.750"));

        assertCliSuccess(db, "import", model, "points", start);
        try (var sql = db.connect()) {
            assertIndex(db, sql, "georoot", "center");
            String object = scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'");
            assertPointHit(db, sql, "moved", "2600100.125", true);
            Path referenceExport = work.resolve("indexed-points-reference.xtf");
            reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", "points", referenceExport.toString());
            assertPointXtf(referenceExport, "moved", "2600100.125");

            runReference(db, "update", "points", legacyUpdate);
            Path nativeExport = work.resolve("indexed-points-native.xtf");
            assertCliSuccess(db, "export", model, "points", nativeExport);
            assertPointXtf(nativeExport, "moved", "2600400.500");
            assertPointHit(db, sql, "moved", "2600400.500", true);
            assertPointHit(db, sql, "moved", "2600100.125", false);

            assertCliSuccess(db, "update", model, "points", update);
            assertEquals(object, scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'"));
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600500.625", true);
            assertPointHit(db, sql, "moved", "2600400.500", false);
            assertPointHit(db, sql, "cleared", "2600200.250", false);
            assertPointHit(db, sql, "dropped", "2600300.375", false);

            Path malformed = Files.writeString(work.resolve("indexed-points-rollback.xtf"),
                    Files.readString(replacement) + "<trailing/>");
            assertNotEquals(0, cli(db, "replace", model, "points", malformed, CancellationToken.none()).code());
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600500.625", true);
            assertPointHit(db, sql, "moved", "2600600.750", false);
            assertCliSuccess(db, "replace", model, "points", replacement);
            assertNotEquals(object, scalar(sql, "SELECT t_id FROM " + db.table("georoot") + " WHERE t_ili_tid='moved'"));
            assertIndexRows(db, sql, 1);
            assertPointHit(db, sql, "moved", "2600600.750", true);
            assertPointHit(db, sql, "moved", "2600500.625", false);

            if (!db.postgis()) {
                String trigger = scalar(sql, "SELECT sql FROM sqlite_schema WHERE name='rtree_georoot_center_update2'");
                execute(sql, "DROP TRIGGER rtree_georoot_center_update2");
                var failed = cli(db, "update", model, "points", update, CancellationToken.none());
                assertNotEquals(0, failed.code(), failed::diagnostics);
                assertTrue(failed.diagnostics().contains("missing index maintenance trigger"), failed::diagnostics);
                assertPointHit(db, sql, "moved", "2600600.750", true);
                execute(sql, trigger);
            }

            assertCliSuccess(db, "delete", model, "points", null);
            assertIndexRows(db, sql, 0);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("georoot")));
            runReference(db, "import", "tail", start);
            assertIndexRows(db, sql, 3);
            reference.run(db, work.toString(), "--delete", "--dataset", "tail");
            assertIndexRows(db, sql, 0);
        }
    }

    private void createReferenceSchema(TestDatabase db, String model) throws Exception {
        Path ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var resource = SpatialIndexCases.class.getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(resource, ili);
            }
        }
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--createGeomIdx", ili.toString());
    }

    private void runReference(TestDatabase db, String operation, String dataset, Path path) throws Exception {
        reference.run(db, work.toString(), "--" + operation, "--dataset", dataset, "--importTid", "--importBid",
                path.toString());
    }

    private Path delivery(String name, String model, String bid, String objects) throws Exception {
        String text = referenceDatasetFixture(model, version, bid, objects);
        if (version == 24) text = text.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        return Files.writeString(work.resolve(name + ".xtf"), text);
    }

    private String feature(String model, String type, String tid, String x) {
        String geometry = x == null ? "" : version == 23
                ? "<Center><COORD><C1>%s</C1><C2>1200000.375</C2></COORD></Center>".formatted(x)
                : "<Center><g:coord><g:c1>%s</g:c1><g:c2>1200000.375</g:c2></g:coord></Center>".formatted(x);
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, geometry)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, geometry);
    }

    private void assertPointXtf(Path path, String tid, String x) throws Exception {
        var objects = children(children(readDataSection(path, version)).getFirst());
        var point = objects.stream().filter(o -> tid.equals(id(o, version, "tid"))).findFirst().orElseThrow();
        var actual = children(children(child(point, "Center")).getFirst()).getFirst().getTextContent();
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(actual)));
    }

    private static void assertIndex(TestDatabase db, Connection sql, String table, String field) throws SQLException {
        String query = db.postgis()
                ? "SELECT count(*) FROM pg_indexes WHERE schemaname='" + db.schema() + "' AND tablename='" + table
                   + "' AND indexdef LIKE '%USING gist%' AND indexdef LIKE '%(" + field + ")%'"
                : "SELECT count(*) FROM gpkg_extensions WHERE table_name='" + table + "' AND column_name='" + field
                   + "' AND extension_name='gpkg_rtree_index' AND scope='write-only'";
        assertEquals("1", scalar(sql, query));
    }

    private static void assertIndexRows(TestDatabase db, Connection sql, int expected) throws SQLException {
        String query = db.postgis() ? "SELECT count(*) FROM " + db.table("georoot") + " WHERE center IS NOT NULL"
                : "SELECT count(*) FROM rtree_georoot_center";
        assertEquals(Integer.toString(expected), scalar(sql, query));
    }

    private static void assertPointHit(TestDatabase db, Connection sql, String tid, String x, boolean expected) throws SQLException {
        String table = db.table("georoot");
        String query = db.postgis() ? "SELECT count(*) FROM " + table + " WHERE t_ili_tid='" + tid
                + "' AND center && ST_MakeEnvelope(" + x + ",1200000," + x + ",1200001,2056)"
                : "SELECT count(*) FROM " + table + " p JOIN rtree_georoot_center idx ON p.t_id=idx.id"
                + " WHERE p.t_ili_tid='" + tid + "' AND idx.minx <= " + x + " AND idx.maxx >= " + x
                + " AND idx.miny <= 1200000.375 AND idx.maxy >= 1200000.375";
        assertEquals(expected ? "1" : "0", scalar(sql, query));
    }
}
