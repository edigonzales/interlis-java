package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static guru.interlis.db.DbInteropTest.*;
import static org.junit.jupiter.api.Assertions.*;

final class DatasetWriteCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;

    DatasetWriteCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
    }

    void replace(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutation" + version;
        schema(db, referenceSchema, model);
        Path first = delivery("original", model, new Basket("main", item(model, "kept", "old")),
                new Basket("obsolete", item(model, "removed", "gone")));
        Path other = delivery("isolated", model, new Basket("other", item(model, "kept", "other dataset")));
        Path replacement = delivery("replacement", model, new Basket("main", item(model, "kept", "changed")));
        seed(db, referenceSchema, model, "main", first);
        seed(db, referenceSchema, model, "other", other);

        try (var sql = db.connect()) {
            var old = rows(db, sql, "mutationitem");
            assertEquals(3, old.size());
            checkConcurrentWriter(db, model, replacement);
            assertEquals(old, rows(db, sql, "mutationitem"));

            Path invalid = Files.writeString(work.resolve("late-error.xtf"), Files.readString(replacement) + "<trailing/>");
            var failure = cli(db, "replace", model, "main", invalid, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertEquals(old, rows(db, sql, "mutationitem"));

            Path exported = mutate(db, referenceSchema, "replace", model, "main", replacement);
            var actual = rows(db, sql, "mutationitem");
            assertEquals(2, actual.size());
            var previous = old.get(key("main", "main", "kept"));
            var current = actual.get(key("main", "main", "kept"));
            assertEquals(previous.dataset(), current.dataset());
            assertNotEquals(previous.basket(), current.basket());
            assertNotEquals(previous.object(), current.object());
            assertEquals("changed", current.label());
            assertEquals(old.get(key("other", "other", "kept")), actual.get(key("other", "other", "kept")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")
                    + " WHERE t_ili_tid = 'obsolete'"));
            assertSingleExport(exported, "main", "kept", "changed");

            Path created = delivery("created", model, new Basket("created", item(model, "new", "new dataset")));
            assertCliSuccess(db, "replace", model, "created", created);
            assertEquals("new dataset", rows(db, sql, "mutationitem").get(key("created", "created", "new")).label());
            runReference(db, "import", "tail", created);
            var tail = rows(db, sql, "mutationitem").get(key("tail", "created", "new"));
            assertTrue(tail.object() > current.object());
        }
    }

    void replaceRelations(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbInterop" + version;
        schema(db, referenceSchema, model);
        Path original = work.resolve("replace-original.xtf");
        if (Files.notExists(original)) {
            try (var resource = DatasetWriteCases.class.getResourceAsStream("/" + model + ".xtf")) {
                Files.copy(resource, original);
            }
        }
        seed(db, referenceSchema, model, "rel", original);
        String fields = "<Links>" + structure(model, "LinkDetail",
                "<Label>in new basket</Label>" + reference("Destination", "site", "")) + "</Links>";
        String geometry = version == 23 ? "<COORD><C1>2600600.500</C1><C2>1200600.250</C2></COORD>"
                : "<g:coord><g:c1>2600600.500</g:c1><g:c2>1200600.250</g:c2></g:coord>";
        String objects = object(model, "Report", "report", fields)
                + object(model, "Place", "site", "<Geometry>" + geometry + "</Geometry>");
        Path replacement = delivery("replace-rel", model, new Basket("new-basket", objects));

        try (var sql = db.connect()) {
            var originalBaskets = new ArrayList<Long>();
            try (var statement = sql.createStatement();
                 var rows = statement.executeQuery("SELECT t_id FROM " + db.table("t_ili2db_basket"))) {
                while (rows.next()) originalBaskets.add(rows.getLong(1));
            }
            assertFalse(originalBaskets.isEmpty());
            Path exported = mutate(db, referenceSchema, "replace", model, "rel", replacement);
            for (String table : List.of("mast", "tower", "nestedleaf", "nestedinner", "markerlink", "place", "linkdetail")) {
                for (long basket : originalBaskets) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table) + " WHERE t_basket = " + basket), table);
                }
            }
            assertEquals("site", scalar(sql, "SELECT p.t_ili_tid FROM " + db.table("place") + " p JOIN "
                    + db.table("linkdetail") + " d ON d.destination=p.t_id"));
            String srid = db.postgis() ? "SELECT ST_SRID(ageometry) FROM " + db.table("place")
                    : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name = 'place'";
            assertEquals("2056", scalar(sql, srid));
            var baskets = children(readDataSection(exported, version));
            assertEquals(1, baskets.size());
            assertEquals("new-basket", id(baskets.getFirst(), version, "bid"));
            var place = child(baskets.getFirst(), version == 23 ? model + ".Data.Place" : "Place");
            assertEquals("site", id(place, version, "tid"));
            String x = children(children(child(place, "Geometry")).getFirst()).getFirst().getTextContent();
            assertEquals(0, new java.math.BigDecimal(x).compareTo(new java.math.BigDecimal("2600600.500")));
        }
    }

    private void checkConcurrentWriter(TestDatabase db, String model, Path input) throws Exception {
        try (var lock = db.connect(); var statement = lock.createStatement()) {
            if (db.postgis()) {
                lock.setAutoCommit(false);
                statement.execute("LOCK TABLE " + db.table("mutationitem") + " IN ROW EXCLUSIVE MODE");
            } else {
                statement.execute("BEGIN IMMEDIATE");
            }
            try {
                var failure = cli(db, "replace", model, "main", input, CancellationToken.none());
                assertNotEquals(0, failure.code(), failure::diagnostics);
                if (db.postgis()) assertTrue(failure.diagnostics().contains("Another writer"), failure::diagnostics);
            } finally {
                if (db.postgis()) lock.rollback();
                else statement.execute("ROLLBACK");
            }
        }
    }

    private void schema(TestDatabase db, boolean referenceSchema, String model) throws Exception {
        Path ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var resource = DatasetWriteCases.class.getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(resource, ili);
            }
        }
        if (referenceSchema) {
            reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                    "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        } else {
            assertCliSuccess(db, "schemaimport", model, null, ili);
        }
    }

    private void runReference(TestDatabase db, String operation, String dataset, Path transfer) throws Exception {
        reference.run(db, work.toString(), "--" + operation, "--importTid", "--importBid", "--dataset", dataset,
                transfer.toString());
    }

    private void seed(TestDatabase db, boolean referenceSchema, String model, String dataset, Path transfer) throws Exception {
        if (referenceSchema) runReference(db, "import", dataset, transfer);
        else assertCliSuccess(db, "import", model, dataset, transfer);
    }

    private Path mutate(TestDatabase db, boolean nativeMutation, String operation, String model, String dataset,
                        Path transfer) throws Exception {
        Path out = work.resolve(operation + "-mixed.xtf");
        Files.deleteIfExists(out);
        if (nativeMutation) {
            assertCliSuccess(db, operation, model, dataset, transfer);
            reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", dataset, out.toString());
        } else {
            runReference(db, operation, dataset, transfer);
            assertCliSuccess(db, "export", model, dataset, out);
        }
        return out;
    }

    private Path delivery(String file, String model, Basket... baskets) throws Exception {
        var first = baskets[0];
        String xml = referenceDatasetFixture(model, version, first.bid(), first.objects());
        if (model.equals("DbInterop23")) xml = xml.replace("VERSION=\"1\"", "VERSION=\"2026-11-20\"");
        String end = version == 23 ? "</DATASECTION>" : "</i:datasection>";
        var extra = new StringBuilder();
        for (int i = 1; i < baskets.length; i++) {
            var basket = baskets[i];
            extra.append(version == 23 ? "<%1$s.Data BID=\"%2$s\">%3$s</%1$s.Data>".formatted(model, basket.bid(), basket.objects())
                    : "<Data i:bid=\"%s\">%s</Data>".formatted(basket.bid(), basket.objects()));
        }
        if (version == 24) {
            xml = xml.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        }
        return Files.writeString(work.resolve(file + ".xtf"), xml.replace(end, extra + end));
    }

    private String item(String model, String tid, String label) {
        return object(model, "MutationItem", tid, "<Label>" + label + "</Label>");
    }

    private String structure(String model, String type, String values) {
        String tag = version == 23 ? model + ".Data." + type : type;
        return "<%1$s>%2$s</%1$s>".formatted(tag, values);
    }

    private String reference(String name, String tid, String bid) {
        if (version == 24) return "<%s i:ref=\"%s\"/>".formatted(name, tid);
        String targetBasket = bid.isEmpty() ? "" : " BID=\"%s\"".formatted(bid);
        return "<%s REF=\"%s\"%s/>".formatted(name, tid, targetBasket);
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

    private static Map<List<String>, Row> rows(TestDatabase db, Connection sql, String table) throws SQLException {
        var result = new HashMap<List<String>, Row>();
        String query = """
                SELECT d.datasetname, b.t_ili_tid, i.t_ili_tid, d.t_id, b.t_id, i.t_id, i.label
                FROM %s i JOIN %s b ON b.t_id = i.t_basket JOIN %s d ON d.t_id = b.dataset
                """.formatted(db.table(table), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"));
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                var key = key(rows.getString(1), rows.getString(2), rows.getString(3));
                assertNull(result.put(key, new Row(rows.getLong(4), rows.getLong(5), rows.getLong(6), rows.getString(7))),
                        key.toString());
            }
        }
        return result;
    }

    private static List<String> key(String dataset, String bid, String tid) { return List.of(dataset, bid, tid); }

    private record Basket(String bid, String objects) { }
    private record Row(long dataset, long basket, long object, String label) { }
}
