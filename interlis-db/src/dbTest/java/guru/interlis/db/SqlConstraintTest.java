package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SqlConstraintTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    private enum Backend { GEOPACKAGE, POSTGIS }

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void uniqueKeysAndRollback(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlUnique";
            schema(db, model, version, """
                    CLASS Asset =
                      RegistryNo : TEXT*24;
                      LocalMark : TEXT*24;
                      VariantNo : 0..10;
                      UNIQUE RegistryNo;
                      UNIQUE LocalMark, VariantNo;
                    END Asset;
                    """, "--create-unique");
            for (String dataset : List.of("first", "second")) {
                assertCliSuccess(db, "import", model, dataset, data(model, version, dataset,
                        "<RegistryNo>" + dataset + "</RegistryNo><LocalMark>" + dataset
                                + "</LocalMark><VariantNo>5</VariantNo>", "", ""));
            }
            var output = work.resolve("unique-export.xtf");
            assertCliSuccess(db, "export", model, "second", output);
            var rows = exportedRows(output, version, "second", 3);
            assertEquals("second", child(rows.getFirst(), "RegistryNo").getTextContent());
            assertNull(child(rows.getLast(), "RegistryNo"));

            String duplicate = "<LocalMark>duplicate</LocalMark><VariantNo>7</VariantNo>";
            String valid = "<RegistryNo>tentative</RegistryNo>";
            String error = importSqlDiagnostic(db, "23505");
            assertCliFailure(db, "import", model, "broken-composite",
                    data(model, version, "broken-composite", valid, duplicate, duplicate), error);
            assertCliFailure(db, "import", model, "broken-global",
                    data(model, version, "broken-global", valid, "<RegistryNo>first</RegistryNo>"), error);
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 6, 2);
                assertEquals("4", scalar(sql, "SELECT count(*) FROM " + db.table("asset")
                        + " WHERE registryno IS NULL AND localmark IS NULL AND variantno IS NULL"));
                assertEquals("first", scalar(sql, "SELECT registryno FROM " + db.table("asset")
                        + " WHERE t_ili_tid='first-0'"));
                assertEquals("second", scalar(sql, "SELECT localmark FROM " + db.table("asset")
                        + " WHERE t_ili_tid='second-0' AND variantno=5"));
            }

            String extra = "SqlUniqueExtra";
            schema(db, extra, version, "CLASS Extra = Badge : TEXT*24; UNIQUE Badge; END Extra;", "--add-models");
            try (var sql = db.connect()) {
                String insert = "INSERT INTO " + db.table("extra") + " (t_id,t_basket,badge) VALUES ";
                String basket = "(SELECT min(t_id) FROM " + db.table("t_ili2db_basket") + ")";
                execute(sql, insert + "(999," + basket + ",'once')");
                var rejected = assertThrows(SQLException.class,
                        () -> execute(sql, insert + "(1000," + basket + ",'once')"));
                assertConstraint(db, rejected, "23505", "SQLITE_CONSTRAINT_UNIQUE");
                assertEquals("once", scalar(sql, "SELECT badge FROM " + db.table("extra")));
            }
        });
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void numericChecksAndRollback(Backend backend, int version) throws Exception {
        withDatabase(backend, db -> {
            String model = "SqlNumeric";
            schema(db, model, version, """
                    DOMAIN SmallReading = -20..30;
                    CLASS Asset =
                      Rating : SmallReading;
                      WideNo : -9223372036854775808..9223372036854775807;
                      Fraction : -2.500..3.125;
                      Precise : -9007199254740993.125..9007199254740993.125;
                    END Asset;
                    """, "--create-num-checks");
            String min = "<Rating>-20</Rating><WideNo>-9007199254740992</WideNo><Fraction>-2.500</Fraction>";
            String max = "<Rating>30</Rating><WideNo>9007199254740992</WideNo><Fraction>3.125</Fraction>";
            assertCliSuccess(db, "import", model, "numbers", data(model, version, "numbers", min, max, ""));
            var output = work.resolve("numeric-export.xtf");
            assertCliSuccess(db, "export", model, "numbers", output);
            var rows = exportedRows(output, version, "numbers", 3);
            assertEquals("-20", child(rows.getFirst(), "Rating").getTextContent());
            assertEquals("30", child(rows.get(1), "Rating").getTextContent());
            assertDecimal("-2.500", child(rows.getFirst(), "Fraction").getTextContent());
            assertDecimal("3.125", child(rows.get(1), "Fraction").getTextContent());
            assertNull(child(rows.getLast(), "Rating"));

            try (var sql = db.connect()) {
                String table = db.table("asset");
                String first = " FROM " + table + " WHERE t_ili_tid='numbers-0'";
                assertEquals("-9007199254740992", scalar(sql, "SELECT wideno" + first));
                assertEquals("9007199254740992", scalar(sql, "SELECT wideno FROM " + table + " WHERE t_ili_tid='numbers-1'"));
                assertNull(scalar(sql, "SELECT rating FROM " + table + " WHERE t_ili_tid='numbers-2'"));
                String row = " WHERE t_ili_tid='numbers-0'";
                execute(sql, "UPDATE " + table + " SET wideno=-9223372036854775808, precise=9007199254740994.0" + row);
                assertEquals("-9223372036854775808", scalar(sql, "SELECT wideno" + first));
                assertDecimal("9007199254740994", scalar(sql, "SELECT precise" + first));
                var rejected = assertThrows(SQLException.class,
                        () -> execute(sql, "UPDATE " + table + " SET precise=9007199254740996.0" + row));
                assertConstraint(db, rejected, "23514", "SQLITE_CONSTRAINT_CHECK");
            }
            String error = importSqlDiagnostic(db, "23514");
            assertCliFailure(db, "import", model, "broken-rating",
                    data(model, version, "broken-rating", min, "<Rating>31</Rating>"), error);
            assertCliFailure(db, "import", model, "broken-fraction",
                    data(model, version, "broken-fraction", min, "<Fraction>-2.501</Fraction>"), error);
            try (var sql = db.connect()) {
                assertRolledBack(db, sql, 3, 1);
                assertEquals("-20", scalar(sql, "SELECT rating FROM " + db.table("asset") + " WHERE t_ili_tid='numbers-0'"));
            }

            schema(db, "SqlNumericExtra", version, "CLASS Extra = Reading : 0..1; END Extra;", "--add-models");
            try (var sql = db.connect()) {
                String table = db.table("extra");
                execute(sql, "INSERT INTO " + table + " (t_id,t_basket,reading) VALUES (999,(SELECT t_id FROM "
                        + db.table("t_ili2db_basket") + "),1)");
                var rejected = assertThrows(SQLException.class, () -> execute(sql, "UPDATE " + table + " SET reading=2"));
                assertConstraint(db, rejected, "23514", "SQLITE_CONSTRAINT_CHECK");
                assertEquals("1", scalar(sql, "SELECT reading FROM " + table));
            }
        });
    }

    private static void assertRolledBack(Target db, Connection sql, int objects, int datasets) throws SQLException {
        assertEquals(Integer.toString(objects), scalar(sql, "SELECT count(*) FROM " + db.table("asset")));
        assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("asset") + " WHERE t_ili_tid LIKE 'broken-%'"));
        assertEquals(Integer.toString(datasets), scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
        assertEquals(Integer.toString(datasets), scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
    }

    private static String importSqlDiagnostic(Target db, String state) {
        return db.postgis() ? "SQL code " + state : "Database operation failed (SQL code ";
    }

    private static void assertConstraint(Target db, SQLException error, String state, String sqliteCode) {
        if (db.postgis()) assertEquals(state, error.getSQLState());
        else assertTrue(error.getMessage().contains(sqliteCode), error::getMessage);
    }

    private static void assertDecimal(String expected, String actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)), actual);
    }

    private void schema(Target db, String model, int version, String declarations, String option) throws Exception {
        var ili = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                %s
                  END Data;
                END %s.
                """.formatted(version - 20, model, declarations, model));
        var result = schemaWithIdentityColumns(db, model, ili, CancellationToken.none(), option);
        assertEquals(0, result.code(), result::diagnostics);
    }

    private Path data(String model, int version, String dataset, String... fields) throws Exception {
        String tag = version == 23 ? model + ".Data.Asset" : "Asset";
        String attribute = version == 23 ? "TID" : "i:tid";
        var objects = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            objects.append('<').append(tag).append(' ').append(attribute).append("=\"").append(dataset)
                    .append('-').append(i).append("\">").append(fields[i]).append("</").append(tag).append('>');
        }
        return Files.writeString(work.resolve(dataset + ".xtf"),
                referenceDatasetFixture(model, version, dataset + "-b", objects.toString()));
    }

    private static List<Element> exportedRows(Path file, int version, String dataset, int count) throws Exception {
        var baskets = children(readDataSection(file, version));
        assertEquals(1, baskets.size());
        assertEquals(dataset + "-b", id(baskets.getFirst(), version, "bid"));
        var rows = children(baskets.getFirst()).stream()
                .sorted(Comparator.comparing(row -> id(row, version, "tid"))).toList();
        assertEquals(count, rows.size());
        return rows;
    }

    @FunctionalInterface
    private interface DatabaseCase {
        void run(Target db) throws Exception;
    }

    private void withDatabase(Backend backend, DatabaseCase test) throws Exception {
        var file = work.resolve("constraints.gpkg");
        if (backend == Backend.GEOPACKAGE) {
            test.run(new Target(file, null, "sql_constraints"));
        } else {
            try (var db = POSTGIS.target(file)) {
                test.run(db);
            }
        }
    }
}
