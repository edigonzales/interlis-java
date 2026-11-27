package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatasetMutationInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;
    private int version;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        this.version = version;
        var legacy = new TestDatabase(work, postgres, "mutation_reference", "v" + version + "_");
        var own = legacy.peer("mutation_own");
        var reference = new ReferenceTool(work, legacy.postgis());
        String model = "DbMutation" + version;
        Path ili = copyModel(model);
        referenceSchema(reference, legacy, ili);
        assertSchemaWithIdentityColumns(own, model, ili);
        lifecycle(reference, legacy, model, true);
        lifecycle(reference, own, model, false);
        orderedUpdate(reference, legacy.peer("ordered_reference"), own.peer("ordered_own"));
        stableUpdate(reference, legacy.peer("stable_reference"), own.peer("stable_own"));
    }

    private void stableUpdate(ReferenceTool reference, TestDatabase legacy, TestDatabase own) throws Exception {
        String model = "DbMutStable" + version;
        Path ili = copyModel(model);
        reference.run(legacy, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        assertCliSuccess(own, "schemaimport", model, null, ili, "--create-basket-col");

        String bid = "72098762-3694-4c89-a663-d0af6687720e";
        String oid = "65cc81e6-72a7-477b-a51f-792fc4f28a15";
        String otherBid = "47fe158a-452c-4e3c-97c5-8c9660a3d11d";
        String otherOid = "69b03972-1c8e-44cd-acfc-ae54034f9ee8";
        var before = delivery("oid-before", model, new Basket(bid, item(model, oid, "before")));
        var update = delivery("oid-update", model, new Basket(bid, item(model, oid, "after")));
        var other = delivery("oid-other", model, new Basket(otherBid, item(model, otherOid, "untouched")));
        for (var db : List.of(legacy, own)) {
            if (db == legacy) {
                reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", "stable", before.toString());
                reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", "other", other.toString());
            } else {
                assertCliSuccess(db, "import", model, "stable", before);
                assertCliSuccess(db, "import", model, "other", other);
            }
            try (var sql = db.connect()) {
                String old = stableIdentity(db, sql, bid, oid, "before");
                String control = stableIdentity(db, sql, otherBid, otherOid, "untouched");
                assertNotNull(old);
                assertNotNull(control);

                Path output = work.resolve((db == legacy ? "oid-reference" : "oid-own") + ".xtf");
                if (db == legacy) {
                    assertCliSuccess(db, "update", model, "stable", update);
                    reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", "stable", output.toString());
                } else {
                    reference.run(db, "%ILI_FROM_DB", "--update", "--dataset", "stable", update.toString());
                    assertCliSuccess(db, "export", model, "stable", output);
                }
                assertSingleExport(output, bid, oid, "after");
                assertEquals(old, stableIdentity(db, sql, bid, oid, "after"));
                assertEquals(control, stableIdentity(db, sql, otherBid, otherOid, "untouched"));
            }
        }
    }

    private static String stableIdentity(TestDatabase db, Connection sql, String bid, String oid, String label)
            throws Exception {
        return scalar(sql, "SELECT b.dataset || ':' || b.t_id || ':' || o.t_id FROM " + db.table("mutationitem")
                + " o JOIN " + db.table("t_ili2db_basket") + " b ON b.t_id=o.t_basket"
                + " WHERE b.t_ili_tid='" + bid + "' AND o.t_ili_tid='" + oid + "' AND o.label='" + label + "'");
    }

    private void lifecycle(ReferenceTool reference, TestDatabase db, String model, boolean referenceSchema) throws Exception {
        var original = delivery("original", model, new Basket("main", item(model, "kept", "initial")),
                new Basket("obsolete", item(model, "removed", "discard")));
        var other = delivery("other", model, new Basket("other", item(model, "other-item", "separate")));
        var update = delivery("update", model, new Basket("main", item(model, "kept", "updated")));
        var replace = delivery("replace", model, new Basket("main", item(model, "kept", "replaced")));

        if (referenceSchema) {
            referenceWrite(reference, db, "import", "main", original);
            referenceWrite(reference, db, "import", "other", other);
        } else {
            assertCliSuccess(db, "import", model, "main", original);
            assertCliSuccess(db, "import", model, "other", other);
        }
        try (var sql = db.connect()) {
            String objects = db.table("mutationitem");
            String keeper = " FROM " + objects + " WHERE t_ili_tid='kept'";
            String old = scalar(sql, "SELECT t_id" + keeper);
            String dataset = datasetId(db, sql, "main");
            String otherId = scalar(sql, "SELECT t_id FROM " + objects + " WHERE t_ili_tid='other-item' AND label='separate'");

            Path output = exchangeWrite(reference, db, model, "main", "update", update, referenceSchema);
            var mainBasket = children(readDataSection(output, version)).stream()
                    .filter(basket -> "main".equals(id(basket, version, "bid"))).findFirst().orElseThrow();
            var updated = children(mainBasket).getFirst();
            assertEquals("kept", id(updated, version, "tid"));
            assertEquals("updated", child(updated, "Label").getTextContent());
            assertEquals(old, scalar(sql, "SELECT t_id" + keeper));
            assertEquals("updated", scalar(sql, "SELECT label" + keeper));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + objects + " WHERE t_ili_tid='removed'"));

            output = exchangeWrite(reference, db, model, "main", "replace", replace, referenceSchema);
            assertSingleExport(output, "main", "kept", "replaced");
            assertEquals(dataset, datasetId(db, sql, "main"));
            String replacementId = scalar(sql, "SELECT t_id" + keeper);
            assertNotEquals(old, replacementId);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + objects + " WHERE t_ili_tid='removed'"));

            if (referenceSchema) assertCliSuccess(db, "delete", model, "main", null);
            else reference.run(db, "%ILI_FROM_DB", "--delete", "--dataset", "main");
            assertNull(datasetId(db, sql, "main"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket") + " WHERE dataset=" + dataset));
            assertNull(scalar(sql, "SELECT t_id" + keeper));
            assertEquals(otherId, scalar(sql, "SELECT t_id FROM " + objects + " WHERE t_ili_tid='other-item' AND label='separate'"));

            if (!referenceSchema) {
                referenceWrite(reference, db, "import", "follow-up", replace);
                assertTrue(Long.parseLong(scalar(sql, "SELECT t_id" + keeper)) > Long.parseLong(replacementId));
            }
        }
    }

    private void orderedUpdate(ReferenceTool reference, TestDatabase legacy, TestDatabase own) throws Exception {
        String model = "DbMutRoles" + version;
        Path ili = copyModel(model);
        referenceSchema(reference, legacy, ili);
        assertSchemaWithIdentityColumns(own, model, ili);
        String targets = object(model, "RoleMarker", "marker", "<Label>marker</Label>")
                + object(model, "RoleHub", "hub", "<Label>hub</Label>");
        Path original = delivery("ordered-original", model, new Basket("roles", targets + visit(model, "marker", "hub", "7")));
        Path update = delivery("ordered-update", model, new Basket("roles", targets + visit(model, "marker", "hub", "17")));

        referenceWrite(reference, legacy, "import", "roles", original);
        assertCliSuccess(own, "import", model, "roles", original);
        Path output = exchangeWrite(reference, legacy, model, "roles", "update", update, true);
        assertVisitPosition(output, "");
        assertCliSuccess(legacy, "export", model, "roles", output);
        assertVisitPosition(output, "17");
        try (var sql = legacy.connect()) {
            assertEquals("17", scalar(sql, "SELECT markervisit_t_seq FROM " + legacy.table("rolevisit")));
        }

        output = exchangeWrite(reference, own, model, "roles", "update", update, false);
        assertVisitPosition(output, "");
        try (var sql = own.connect()) {
            assertNull(scalar(sql, "SELECT markervisit_t_seq FROM " + own.table("rolevisit")));
        }
    }

    private void assertVisitPosition(Path output, String expected) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        var visit = child(basket, version == 23 ? "DbMutRoles23.Data.RoleVisit" : "RoleVisit");
        var marker = child(visit, "MarkerVisit");
        assertEquals("marker", id(marker, version, "ref"));
        assertEquals("hub", id(child(visit, "HubVisit"), version, "ref"));
        assertEquals(expected, version == 23 ? marker.getAttribute("ORDER_POS") : id(marker, version, "order_pos"));
    }

    private Path exchangeWrite(ReferenceTool reference, TestDatabase db, String model, String dataset, String operation,
                               Path input, boolean referenceSchema) throws Exception {
        Path output = work.resolve("mixed-" + operation + ".xtf");
        Files.deleteIfExists(output);
        if (referenceSchema) {
            assertCliSuccess(db, operation, model, dataset, input);
            reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
        } else {
            referenceWrite(reference, db, operation, dataset, input);
            assertCliSuccess(db, "export", model, dataset, output);
        }
        return output;
    }

    private static String datasetId(TestDatabase db, Connection sql, String name) throws Exception {
        return scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + name + "'");
    }

    private void referenceSchema(ReferenceTool reference, TestDatabase db, Path ili) throws Exception {
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
    }

    private static void referenceWrite(ReferenceTool reference, TestDatabase db, String operation, String dataset, Path input)
            throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--" + operation, "--dataset", dataset, "--importTid", "--importBid", input.toString());
    }

    private Path copyModel(String model) throws Exception {
        Path file = work.resolve(model + ".ili");
        try (var source = DatasetMutationInteropTest.class.getResourceAsStream("/" + file.getFileName())) {
            Files.copy(java.util.Objects.requireNonNull(source, model), file);
        }
        return file;
    }

    private Path delivery(String file, String model, Basket... baskets) throws Exception {
        var first = baskets[0];
        String xml = referenceDatasetFixture(model, version, first.bid(), first.objects());
        String end = version == 23 ? "</DATASECTION>" : "</i:datasection>";
        var extra = new StringBuilder();
        for (int i = 1; i < baskets.length; i++) {
            var basket = baskets[i];
            extra.append(version == 23 ? "<%1$s.Data BID=\"%2$s\">%3$s</%1$s.Data>".formatted(model, basket.bid(), basket.objects())
                    : "<Data i:bid=\"%s\">%s</Data>".formatted(basket.bid(), basket.objects()));
        }
        return Files.writeString(work.resolve(file + ".xtf"), xml.replace(end, extra + end));
    }

    private String item(String model, String tid, String label) {
        return object(model, "MutationItem", tid, "<Label>" + label + "</Label>");
    }

    private String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private void assertSingleExport(Path path, String bid, String tid, String label) throws Exception {
        var baskets = children(readDataSection(path, version));
        assertEquals(1, baskets.size());
        assertEquals(bid, id(baskets.getFirst(), version, "bid"));
        var objects = children(baskets.getFirst());
        assertEquals(1, objects.size());
        assertEquals(tid, id(objects.getFirst(), version, "tid"));
        assertEquals(label, child(objects.getFirst(), "Label").getTextContent());
    }

    private String visit(String model, String marker, String hub, String position) {
        return version == 23
                ? "<%1$s.Data.RoleVisit><MarkerVisit REF=\"%2$s\" ORDER_POS=\"%4$s\"/><HubVisit REF=\"%3$s\"/></%1$s.Data.RoleVisit>"
                    .formatted(model, marker, hub, position)
                : "<RoleVisit><MarkerVisit i:ref=\"%s\" i:order_pos=\"%s\"/><HubVisit i:ref=\"%s\"/></RoleVisit>"
                    .formatted(marker, position, hub);
    }

    private record Basket(String bid, String objects) { }
}
