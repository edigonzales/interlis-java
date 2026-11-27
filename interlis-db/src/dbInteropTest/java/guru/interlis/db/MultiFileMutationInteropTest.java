package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.sql.Connection;
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
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MultiFileMutationInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageUpdate23() throws Exception { exchangeUpdate(23, null); }
    @Test void geoPackageUpdate24() throws Exception { exchangeUpdate(24, null); }
    @Test void postgisUpdate23() throws Exception { exchangeUpdate(23, POSTGIS.container()); }
    @Test void postgisUpdate24() throws Exception { exchangeUpdate(24, POSTGIS.container()); }

    private void exchangeUpdate(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new MultiFileImportFixtures(work, version);
        var legacy = new TestDatabase(work, postgres, "batch_update_legacy", "v" + version + "_");
        var own = legacy.peer("batch_update_own");
        var reference = new ReferenceTool(work, legacy.postgis());
        schema(reference, legacy, work, fixture.model, "newClass", "--createImportTabs");
        assertSchemaWithIdentityColumns(own, fixture.model, fixture.source, "--create-import-tabs");
        var original = fixture.delivery("original",
                fixture.basket("left", fixture.place("a", "Initially left"), fixture.place("obsolete", "Remove")),
                fixture.basket("right", fixture.place("b", "Initially right")),
                fixture.basket("skipped", fixture.place("c", "Preserve")));
        importDataset(reference, legacy, "existing", original);
        assertCliSuccess(own, "import", fixture.model, "existing", original);
        var beforeLegacy = retainedIds(legacy);
        var beforeOwn = retainedIds(own);

        var first = fixture.delivery("update-first",
                fixture.basket("left", fixture.place("a", "Updated left"), fixture.externalSurvey("s-a", "b", "right")),
                fixture.basket("skipped", fixture.place("c", "Ignored")));
        var last = fixture.delivery("update-last",
                fixture.basket("right", fixture.place("b", "Updated right"), fixture.externalSurvey("s-b", "a", "left")));
        assertCliSuccess(legacy, "update", fixture.model, "existing", last,
                "--basket", "left", "--basket", "right", "--topic", fixture.model + ".Data", first.toString());
        reference.run(own, "%ILI_FROM_DB", "--update", "--importBid", "--importTid", "--dataset", "existing",
                "--baskets", "left;right", "--topics", fixture.model + ".Data", first + ";" + last);

        assertEquals(beforeLegacy, retainedIds(legacy));
        assertEquals(beforeOwn, retainedIds(own));
        for (var db : List.of(legacy, own)) {
            try (var sql = db.connect()) {
                assertCrossedReferences(db, sql);
                assertEquals(List.of(first + ":left:2", last + ":right:2"), changedBasketStatistics(db, sql, first, last));
            }
        }
        var legacyOutput = work.resolve("reference-export.xtf");
        var ownOutput = work.resolve("native-export.xtf");
        exportDataset(reference, legacy, "existing", legacyOutput);
        assertCliSuccess(own, "export", fixture.model, "existing", ownOutput);
        var expected = Set.of("left:a:Updated left", "left:s-a:b", "right:b:Updated right", "right:s-b:a", "skipped:c:Preserve");
        assertExport(legacyOutput, version, expected);
        assertExport(ownOutput, version, expected);
    }

    private static Set<String> retainedIds(TestDatabase db) throws Exception {
        try (var sql = db.connect()) {
            return catalogKeys(sql, """
                    SELECT 'dataset:' || datasetname || ':' || t_id FROM %s
                    UNION ALL SELECT 'basket:' || t_ili_tid || ':' || t_id FROM %s
                    UNION ALL SELECT 'place:' || t_ili_tid || ':' || t_id FROM %s WHERE t_ili_tid <> 'obsolete'
                    """.formatted(db.table("t_ili2db_dataset"), db.table("t_ili2db_basket"), db.table("place")));
        }
    }

    private static void assertCrossedReferences(TestDatabase db, Connection sql) throws Exception {
        assertEquals(Set.of("left:s-a:right:b", "right:s-b:left:a"), catalogKeys(sql, """
                SELECT sb.t_ili_tid || ':' || s.t_ili_tid || ':' || pb.t_ili_tid || ':' || p.t_ili_tid
                FROM %s s JOIN %s r ON r.survey_pointervalue=s.t_id JOIN %s p ON p.t_id=r.destination
                JOIN %s sb ON sb.t_id=s.t_basket JOIN %s pb ON pb.t_id=p.t_basket
                """.formatted(db.table("survey"), db.table("pointer"), db.table("place"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_basket"))));
    }

    private static List<String> changedBasketStatistics(TestDatabase db, Connection sql, Path first, Path last) throws Exception {
        var stats = new ArrayList<String>();
        try (var query = sql.prepareStatement("""
                SELECT i.importfile || ':' || b.t_ili_tid || ':' || s.objectcount
                FROM %s i JOIN %s s ON i.t_id=s.importrun JOIN %s b ON b.t_id=s.basket AND b.dataset=i.dataset
                WHERE i.importfile IN (?, ?) ORDER BY i.t_id
                """.formatted(db.table("t_ili2db_import"), db.table("t_ili2db_import_basket"), db.table("t_ili2db_basket")))) {
            query.setString(1, first.toString());
            query.setString(2, last.toString());
            try (var rows = query.executeQuery()) {
                while (rows.next()) stats.add(rows.getString(1));
            }
        }
        return stats;
    }

    private static void assertExport(Path output, int version, Set<String> expected) throws Exception {
        var entries = new HashSet<String>();
        int objects = 0;
        for (var basket : children(readDataSection(output, version))) {
            String bid = id(basket, version, "bid");
            for (var object : children(basket)) {
                objects++;
                String tid = id(object, version, "tid");
                String value = object.getLocalName().endsWith("Place") ? child(object, "Label").getTextContent()
                        : id(child(children(child(object, "PointerValue")).getFirst(), "Destination"), version, "ref");
                entries.add(bid + ":" + tid + ":" + value);
            }
        }
        assertEquals(expected.size(), objects);
        assertEquals(expected, entries);
    }
}
