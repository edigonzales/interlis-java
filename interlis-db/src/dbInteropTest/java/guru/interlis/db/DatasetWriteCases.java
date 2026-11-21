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
