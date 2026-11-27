package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiFileImportInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchange(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { exchange(24, POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new MultiFileImportFixtures(work, version);
        var referenceSchema = new TestDatabase(work, postgres, "batch_reference", "v" + version + "_");
        var nativeSchema = referenceSchema.peer("batch_native");
        var reference = new ReferenceTool(work, referenceSchema.postgis());
        schema(reference, referenceSchema, work, fixture.model, "newClass", "--createImportTabs");
        assertSchemaWithIdentityColumns(nativeSchema, fixture.model, fixture.source, "--create-import-tabs");
        Path first = fixture.delivery("first", fixture.basket("first", fixture.place("left", "Left"),
                fixture.externalSurvey("survey-left", "right", "second")));
        Path last = fixture.delivery("last file", fixture.basket("second", fixture.place("right", "Right"),
                fixture.externalSurvey("survey-right", "left", "first")));

        assertCliSuccess(referenceSchema, "import", fixture.model, null, last, first.toString());
        reference.run(nativeSchema, "%ILI_FROM_DB", "--import", "--importBid", "--importTid", first + ";" + last);

        String inReference = assertStoredBatch(referenceSchema, first, last);
        String inNative = assertStoredBatch(nativeSchema, first, last);
        Path referenceOutput = work.resolve("reference.xtf");
        Path nativeOutput = work.resolve("native.xtf");
        exportDataset(reference, referenceSchema, inReference, referenceOutput);
        assertCliSuccess(nativeSchema, "export", fixture.model, inNative, nativeOutput);
        assertExport(fixture.model, version, referenceOutput);
        assertExport(fixture.model, version, nativeOutput);
    }

    private static String assertStoredBatch(TestDatabase db, Path first, Path last) throws Exception {
        try (var sql = db.connect()) {
            var dataset = db.table("t_ili2db_dataset");
            var basket = db.table("t_ili2db_basket");
            var imports = db.table("t_ili2db_import");
            var statistics = db.table("t_ili2db_import_basket");
            String name = "lastfile.xtf-" + scalar(sql, "SELECT t_id FROM " + dataset);
            assertEquals(name, scalar(sql, "SELECT datasetname FROM " + dataset));
            assertEquals(Set.of(name + ":first", name + ":second"), catalogKeys(sql, """
                    SELECT d.datasetname || ':' || b.t_ili_tid FROM %s b JOIN %s d ON d.t_id=b.dataset
                    """.formatted(basket, dataset)));
            assertEquals(Set.of("first:left:Left", "second:right:Right"), catalogKeys(sql, """
                    SELECT b.t_ili_tid || ':' || p.t_ili_tid || ':' || p.label
                    FROM %s p JOIN %s b ON b.t_id=p.t_basket
                    """.formatted(db.table("place"), basket)));
            assertEquals(Set.of("first:survey-left:second:right", "second:survey-right:first:left"), catalogKeys(sql, """
                    SELECT sb.t_ili_tid || ':' || s.t_ili_tid || ':' || pb.t_ili_tid || ':' || p.t_ili_tid
                    FROM %s s JOIN %s r ON r.survey_pointervalue=s.t_id JOIN %s p ON p.t_id=r.destination
                    JOIN %s sb ON sb.t_id=s.t_basket JOIN %s pb ON pb.t_id=p.t_basket
                    """.formatted(db.table("survey"), db.table("pointer"), db.table("place"), basket, basket)));
            assertEquals(List.of(first + ":first:2", last + ":second:2"), auditRows(sql, """
                    SELECT i.importfile || ':' || b.t_ili_tid || ':' || s.objectcount
                    FROM %s s JOIN %s i ON i.t_id=s.importrun JOIN %s b ON b.t_id=s.basket
                    JOIN %s d ON d.t_id=i.dataset AND d.t_id=b.dataset ORDER BY i.t_id
                    """.formatted(statistics, imports, basket, dataset)));
            return name;
        }
    }

    private static List<String> auditRows(Connection sql, String query) throws SQLException {
        var runs = new ArrayList<String>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) runs.add(rows.getString(1));
        }
        return runs;
    }

    private static void assertExport(String model, int version, Path output) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data, version));
        var entries = new HashSet<String>();
        var baskets = children(data);
        assertEquals(2, baskets.size());
        for (var basket : baskets) {
            String bid = id(basket, version, "bid");
            assertEquals(2, children(basket).size(), bid);
            for (var object : children(basket)) {
                String tid = id(object, version, "tid");
                String value = object.getLocalName().endsWith("Place")
                        ? child(object, "Label").getTextContent()
                        : id(child(children(child(object, "PointerValue")).getFirst(), "Destination"), version, "ref");
                entries.add(bid + ":" + tid + ":" + value);
            }
        }
        assertEquals(Set.of("first:left:Left", "first:survey-left:right", "second:right:Right", "second:survey-right:left"),
                entries);
    }
}
