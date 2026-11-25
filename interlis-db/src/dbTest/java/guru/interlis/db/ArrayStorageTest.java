package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static guru.interlis.db.ArrayFixtures.storedItems;
import static guru.interlis.db.ArrayScalarFixtures.LABELS;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ArrayStorageTest {
    private static final String MODEL = "ArrayStorage";
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    private enum Backend { GEOPACKAGE, POSTGIS }

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    private static Stream<Arguments> fullTransfers() {
        return Arrays.stream(Backend.values()).flatMap(backend ->
                Stream.of("2.3", "2.4").map(version -> Arguments.of(backend, version)));
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @MethodSource("fullTransfers")
    void textValuesAndInvalidElements(Backend backend, String version) throws Exception {
        withDatabase(backend, db -> {
            createSchema(db, version, "TEXT*40", true);
            var input = Arrays.asList("é, \"A\"", "duplicate", "duplicate", null, "");
            importArray(db, version, input);
            var output = work.resolve("export.xtf");
            var seeded = Arrays.asList("before", null, "");
            try (var sql = db.connect()) {
                assertStoredArray(db, sql, "character varying[]", input);
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("elem")));
                assertNull(scalar(sql, "SELECT items FROM " + db.table("asset") + " WHERE t_ili_tid='native-missing'"));
                setItems(db, sql, "native-value", db.postgis() ? "ARRAY['before',NULL,'']" : "'[\"before\",null,\"\"]'");
                setItems(db, sql, "native-missing", db.postgis() ? "'{}'" : "'[]'");
                assertCliSuccess(db, "export", MODEL, "native", output);
                assertWrappedExport(output, version, seeded);
                setItems(db, sql, "native-value", db.postgis() ? "'{{a},{b}}'" : "'[{}]'");
            }
            assertFailedExportKeepsFile(db, output, db.postgis()
                    ? "one-dimensional PostgreSQL array" : "Expected a text array element");
        });
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @MethodSource("fullTransfers")
    void booleanStorageAndWrongType(Backend backend, String version) throws Exception {
        withDatabase(backend, db -> {
            createSchema(db, version, "BOOLEAN", true);
            importArray(db, version, List.of("true", "false", "true"));
            var output = work.resolve("export.xtf");
            String diagnostic;
            try (var sql = db.connect()) {
                if (db.postgis()) {
                    assertStoredArray(db, sql, "boolean[]", List.of("true", "false", "true"));
                } else {
                    assertEquals("[true,false,true]", scalar(sql, "SELECT items FROM " + db.table("asset")
                            + " WHERE t_ili_tid='native-value'"));
                }
                assertNull(scalar(sql, "SELECT items FROM " + db.table("asset") + " WHERE t_ili_tid='native-missing'"));
                setItems(db, sql, "native-value", db.postgis() ? "ARRAY[false,true,false]" : "'[false,true,false]'");
                assertCliSuccess(db, "export", MODEL, "native", output);
                assertWrappedExport(output, version, List.of("false", "true", "false"));
                if (db.postgis()) {
                    execute(sql, "ALTER TABLE " + db.table("asset") + " ALTER COLUMN items TYPE integer[] USING NULL");
                    diagnostic = "Expected _bool for array column";
                } else {
                    setItems(db, sql, "native-value", "'[\"true\"]'");
                    diagnostic = "Expected a boolean array element";
                }
            }
            assertFailedExportKeepsFile(db, output, diagnostic);
        });
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @MethodSource("fullTransfers")
    void integerStorageAndLateOverflowRollback(Backend backend, String version) throws Exception {
        withDatabase(backend, db -> {
            createSchema(db, version, "-20 .. 20", true);
            var initial = List.of("-5", "0", "12", "-5");
            importArray(db, version, initial);
            try (var sql = db.connect()) {
                assertStoredArray(db, sql, "integer[]", initial);
                if (!db.postgis()) {
                    assertEquals("integer", scalar(sql, "SELECT json_type(items, '$[0]') FROM " + db.table("asset")
                            + " WHERE t_ili_tid='native-value'"));
                }
                var seeded = List.of("-8", "0", "9", "-8");
                setItems(db, sql, "native-value", db.postgis() ? "ARRAY[-8,0,9,-8]" : "'[-8,0,9,-8]'");
                var output = work.resolve("export.xtf");
                assertCliSuccess(db, "export", MODEL, "native", output);
                assertWrappedExport(output, version, seeded);

                String first = asset(version, "rollback-first", List.of("3"));
                String last = asset(version, "rollback-last", List.of("2147483648"));
                assertCliFailure(db, "import", MODEL, "rollback", dataset(version, "rollback", first + last),
                        "32-bit integer encoding");
                assertStoredArray(db, sql, "integer[]", seeded);
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE t_ili_tid LIKE 'rollback-%'"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            }
        });
    }

    @ParameterizedTest(name = "{0}, XTF 2.4")
    @EnumSource(Backend.class)
    void directNullElementKeepsExport(Backend backend) throws Exception {
        withDatabase(backend, db -> {
            createSchema(db, "2.4", "TEXT*40", false);
            var input = dataset("2.4", "native", "<Asset i:tid=\"native-value\"/>");
            assertCliSuccess(db, "import", MODEL, "native", input);
            var output = work.resolve("export.xtf");
            assertCliSuccess(db, "export", MODEL, "native", output);
            try (var sql = db.connect()) {
                setItems(db, sql, "native-value", db.postgis() ? "'{value,NULL}'" : "'[\"value\",null]'");
            }
            assertFailedExportKeepsFile(db, output, "Null element in direct coalesced array");
        });
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @MethodSource("fullTransfers")
    void renamedArrayColumnAndSingleValueCardinality(Backend backend, String version) throws Exception {
        withDatabase(backend, db -> {
            var fixture = new ArrayScalarFixtures(work, xtfVersion(version));
            fixture.schema(db);
            String column = fixture.wrappedText.toLowerCase(Locale.ROOT);
            try (var sql = db.connect()) {
                execute(sql, "ALTER TABLE " + db.table("asset") + " RENAME COLUMN " + column + " TO stored_items");
                execute(sql, "UPDATE " + db.table("t_ili2db_attrname")
                        + " SET sqlname='stored_items' WHERE colowner='asset' AND sqlname='" + column + "'");
                execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                        + " SET columnname='stored_items' WHERE tablename='asset' AND columnname='" + column + "'");
            }

            assertCliSuccess(db, "import", fixture.model, "native", fixture.input);
            Path output = work.resolve("published.xtf");
            assertCliSuccess(db, "export", fixture.model, "native", output);
            fixture.assertDelivery(output, "fixture", false);
            byte[] previous = Files.readAllBytes(output);
            try (var sql = db.connect()) {
                assertEquals(LABELS, storedItems(db, sql, "asset", "stored_items", "fixture-value", LABELS.size()));
                assertEquals(List.of("one"), storedItems(db, sql, "asset", "single", "fixture-value", 1));
                assertNull(scalar(sql, "SELECT single FROM " + db.table("asset") + " WHERE t_ili_tid='fixture-missing'"));
                String multiple = db.postgis() ? "'{first,second}'" : "'[\"first\",\"second\"]'";
                execute(sql, "UPDATE " + db.table("asset") + " SET single=" + multiple + " WHERE t_ili_tid='fixture-value'");
            }
            assertCliFailure(db, "export", fixture.model, "native", output, "Single coalesced structure has multiple array elements");
            assertArrayEquals(previous, Files.readAllBytes(output));
        });
    }

    @Test
    void failedImportLeavesConcurrentPostgisSchemaIntact() throws Exception {
        var file = work.resolve("array.gpkg");
        var good = POSTGIS.target(file);
        var bad = POSTGIS.target(file);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            createSchema(good, "2.4", "-20 .. 20", true);
            createSchema(bad, "2.4", "-20 .. 20", true);
            String first = asset("2.4", "first", List.of("3"));
            var goodInput = Files.writeString(work.resolve("good.xtf"), referenceDatasetFixture(MODEL, 24,
                    "same-basket", first + asset("2.4", "last", List.of("7"))));
            var badInput = Files.writeString(work.resolve("bad.xtf"), referenceDatasetFixture(MODEL, 24,
                    "same-basket", first + asset("2.4", "last", List.of("2147483648"))));
            var start = new CyclicBarrier(2);
            var success = executor.submit(() -> {
                start.await(60, TimeUnit.SECONDS);
                assertCliSuccess(good, "import", MODEL, "same", goodInput);
                return null;
            });
            var failure = executor.submit(() -> {
                start.await(60, TimeUnit.SECONDS);
                assertCliFailure(bad, "import", MODEL, "same", badInput, "32-bit integer encoding");
                return null;
            });
            success.get(60, TimeUnit.SECONDS);
            failure.get(60, TimeUnit.SECONDS);
            try (var sql = good.connect()) {
                assertEquals("3", scalar(sql, "SELECT items[1] FROM " + good.table("asset") + " WHERE t_ili_tid='first'"));
                assertEquals("7", scalar(sql, "SELECT items[1] FROM " + good.table("asset") + " WHERE t_ili_tid='last'"));
                assertEquals("2", scalar(sql, "SELECT count(*) FROM " + good.table("asset")));
                assertEquals("same-basket", scalar(sql, "SELECT t_ili_tid FROM " + good.table("t_ili2db_basket")));
                assertEquals("same", scalar(sql, "SELECT datasetname FROM " + good.table("t_ili2db_dataset")));
            }
            try (var sql = bad.connect()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + bad.table("asset")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + bad.table("t_ili2db_basket")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + bad.table("t_ili2db_dataset")));
            }
        }
    }

    private void createSchema(Target db, String version, String type, boolean wrapped) throws Exception {
        var ili = Files.writeString(work.resolve(MODEL + ".ili"), """
                INTERLIS %s;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    %s
                    CLASS Asset =
                      !!@ili2db.mapping=ARRAY
                      Items : LIST OF %s;
                    END Asset;
                  END Data;
                END %s.
                """.formatted(version, MODEL, wrapped ? "STRUCTURE Elem = Entry : " + type + "; END Elem;" : "",
                        wrapped ? "Elem" : type, MODEL));
        var result = schemaWithIdentityColumns(db, MODEL, ili, CancellationToken.none(), "--coalesce-array");
        assertEquals(0, result.code(), result::diagnostics);
    }

    private void importArray(Target db, String version, List<String> input) throws Exception {
        String objects = asset(version, "native-value", input) + asset(version, "native-missing", List.of());
        assertCliSuccess(db, "import", MODEL, "native", dataset(version, "native", objects));
    }

    private Path dataset(String version, String dataset, String objects) throws Exception {
        return Files.writeString(work.resolve(dataset + ".xtf"),
                referenceDatasetFixture(MODEL, xtfVersion(version), dataset + "-b", objects));
    }

    private static String asset(String version, String tid, List<String> values) {
        boolean ili23 = version.equals("2.3");
        String prefix = ili23 ? MODEL + ".Data." : "";
        String elem = prefix + "Elem";
        String items = values.stream().map(value -> "<" + elem + ">"
                + (value == null ? "" : "<Entry>" + value.replace("&", "&amp;").replace("<", "&lt;") + "</Entry>")
                + "</" + elem + ">").collect(Collectors.joining());
        return "<" + prefix + "Asset " + (ili23 ? "TID" : "i:tid") + "=\"" + tid + "\">"
                + (items.isEmpty() ? "" : "<Items>" + items + "</Items>") + "</" + prefix + "Asset>";
    }

    private static void assertStoredArray(Target db, Connection sql, String pgType, List<String> expected) throws Exception {
        String row = " FROM " + db.table("asset") + " WHERE t_ili_tid='native-value'";
        if (db.postgis()) assertEquals(pgType, scalar(sql, "SELECT pg_typeof(items)::text" + row));
        String size = db.postgis() ? "cardinality(items)" : "json_array_length(items)";
        assertEquals(Integer.toString(expected.size()), scalar(sql, "SELECT " + size + row));
        var values = new ArrayList<String>();
        for (int i = 0; i < expected.size(); i++) {
            String item = db.postgis() ? "items[" + (i + 1) + "]::text" : "json_extract(items, '$[" + i + "]')";
            values.add(scalar(sql, "SELECT " + item + row));
        }
        assertEquals(expected, values);
    }

    private static void setItems(Target db, Connection sql, String tid, String value) throws Exception {
        execute(sql, "UPDATE " + db.table("asset") + " SET items=" + value + " WHERE t_ili_tid='" + tid + "'");
    }

    private static void assertWrappedExport(Path output, String version, List<String> expected) throws Exception {
        int wireVersion = xtfVersion(version);
        var baskets = children(readDataSection(output, wireVersion));
        assertEquals(1, baskets.size());
        assertEquals("native-b", id(baskets.getFirst(), wireVersion, "bid"));
        var objects = children(baskets.getFirst()).stream()
                .collect(Collectors.toMap(object -> id(object, wireVersion, "tid"), object -> object));
        assertEquals(2, objects.size());
        assertNull(child(objects.get("native-missing"), "Items"));
        var values = children(objects.get("native-value")).stream().filter(field -> field.getLocalName().equals("Items"))
                .flatMap(field -> children(field).stream()).map(entry -> child(entry, "Entry"))
                .map(entry -> entry == null ? null : entry.getTextContent()).toList();
        assertEquals(expected, values);
    }

    private static int xtfVersion(String version) { return version.equals("2.3") ? 23 : 24; }

    private static void assertFailedExportKeepsFile(Target db, Path output, String diagnostic) throws Exception {
        byte[] previous = Files.readAllBytes(output);
        assertCliFailure(db, "export", MODEL, "native", output, diagnostic);
        assertArrayEquals(previous, Files.readAllBytes(output));
    }

    @FunctionalInterface
    private interface DatabaseCase {
        void run(Target db) throws Exception;
    }

    private void withDatabase(Backend backend, DatabaseCase test) throws Exception {
        var file = work.resolve("array.gpkg");
        if (backend == Backend.GEOPACKAGE) {
            test.run(new Target(file, null, "array_storage"));
        } else {
            test.run(POSTGIS.target(file));
        }
    }
}
