package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.StructureMappingFixtures.field;
import static guru.interlis.db.StructureMappingFixtures.jsonKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class StructureMappingStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void expandedPositionsAndAtomicFailures(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixtures = new StructureMappingFixtures(work, version);
        String model = "ExpandSchema" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.expandModel(model), "--expand-struct");
        try (var sql = db.connect()) {
            String asset = db.table("asset");
            execute(sql, "ALTER TABLE " + asset + " RENAME COLUMN detail0_label1 TO stored_label");
            execute(sql, "UPDATE " + db.table("t_ili2db_attrname") + " SET sqlname='stored_label' WHERE sqlname='detail0_label1'");
            execute(sql, "UPDATE " + db.table("t_ili2db_column_prop")
                    + " SET columnname='stored_label' WHERE columnname='detail0_label1'");
            Path output = roundTrip(db, model, "native", fixtures.expandDataset(model, "native"));
            fixtures.assertExpanded(output, "native");
            String value = " FROM " + asset + " WHERE t_ili_tid='native-value'";
            assertEquals("ordinary", scalar(sql, "SELECT detail0_label" + value));
            assertEquals("é & a", scalar(sql, "SELECT stored_label" + value));
            assertEquals("entry", scalar(sql, "SELECT detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-detail-empty'"));
            assertNull(scalar(sql, "SELECT stored_label FROM " + asset + " WHERE t_ili_tid='native-detail-empty'"));
            assertNull(scalar(sql, "SELECT detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-absent'"));
            assertEquals("b", scalar(sql, "SELECT items4_label" + value));
            assertEquals("entry", scalar(sql, "SELECT items2_t_type" + value));
            assertNull(scalar(sql, "SELECT items2_label" + value));
            assertEquals("entry", scalar(sql, "SELECT bagged2_t_type" + value));
            assertNull(scalar(sql, "SELECT bagged2_label" + value));
            assertNull(scalar(sql, "SELECT bagged3_t_type" + value));
            assertNull(scalar(sql, "SELECT bagged0_t_type FROM " + asset + " WHERE t_ili_tid='native-absent'"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("entry") + " WHERE asset_larger IS NOT NULL"));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("entry")));

            String wrapper = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname")
                    + " WHERE iliname='" + model + ".Data.Wrapper'");
            String withoutInner = " FROM " + asset + " WHERE t_ili_tid='native-without-inner'";
            assertEquals(wrapper, scalar(sql, "SELECT nest0_t_type" + withoutInner));
            assertNull(scalar(sql, "SELECT nest0_detail0_t_type" + withoutInner));
            assertEquals("entry", scalar(sql, "SELECT nest0_detail0_t_type FROM " + asset + " WHERE t_ili_tid='native-nested-empty'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(wrapper)));

            String empty = fixtures.entry(model, "");
            var failing = fixtures.dataset(model, "rollback", fixtures.asset(model, "first-row", field("Items", empty))
                    + fixtures.asset(model, "last-row", field("Items", fixtures.fiveItems(model) + empty)));
            assertCliFailure(db, "import", model, "rollback", failing, "Expanded collection exceeds 5 stored positions");
            assertEquals("5", scalar(sql, "SELECT count(*) FROM " + asset));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertNull(scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='rollback'"));

            execute(sql, "UPDATE " + asset + " SET detail0_t_type='unknown' WHERE t_ili_tid='native-value'");
            assertExportRejected(db, model, "native", output, "Unexpected expanded structure type unknown");
            execute(sql, "UPDATE " + asset + " SET detail0_t_type='entry', nest0_t_type=NULL WHERE t_ili_tid='native-value'");
            assertExportRejected(db, model, "native", output, "Missing expanded structure type at " + model + ".Data.Asset.Nest[0]");
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void inheritedExpandedValuesDuringMutations(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixtures = new StructureMappingFixtures(work, version);
        String model = "ExpandSmart" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.smartModel(model), "--inheritance", "smart2", "--expand-struct");
        var initial = List.of("first", "second");
        Path output = roundTrip(db, model, "native", fixtures.smartDataset(model, "native", initial));
        fixtures.assertSmart(output, "native", initial);
        try (var sql = db.connect()) {
            String first = db.table("firstasset");
            String other = db.table("otherasset");
            String identity = "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text) FROM ";
            var beforeFirst = scalar(sql, identity + first);
            var beforeOther = scalar(sql, identity + other);
            var beforeDataset = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset"));
            assertEquals("second", scalar(sql, "SELECT items1_label FROM " + first));
            assertEquals("entry", scalar(sql, "SELECT items1_t_type FROM " + first));
            assertEquals("other", scalar(sql, "SELECT items0_label FROM " + other));

            var updated = List.of("updated");
            assertCliSuccess(db, "update", model, "native", fixtures.smartDataset(model, "native", updated));
            assertEquals(beforeFirst, scalar(sql, identity + first));
            assertEquals(beforeOther, scalar(sql, identity + other));
            assertNull(scalar(sql, "SELECT items1_label FROM " + first));
            assertNull(scalar(sql, "SELECT items1_t_type FROM " + first));
            assertCliSuccess(db, "export", model, "native", output);
            fixtures.assertSmart(output, "native", updated);

            assertCliSuccess(db, "replace", model, "native", fixtures.smartDataset(model, "native", List.of()));
            assertNotEquals(beforeFirst, scalar(sql, identity + first));
            assertNotEquals(beforeOther, scalar(sql, identity + other));
            assertEquals(beforeDataset, scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset")));
            assertNull(scalar(sql, "SELECT items0_t_type FROM " + first));
            assertCliSuccess(db, "export", model, "native", output);
            fixtures.assertSmart(output, "native", List.of());
            assertCliSuccess(db, "delete", model, "native", null);
            for (var table : List.of(first, other, db.table("entry"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"))) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + table), table);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void jsonTypesAndAtomicExport(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixtures = new StructureMappingFixtures(work, version);
        String model = "JsonSchema" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.mixedModel(model), "--expand-struct", "--coalesce-json");
        Path output = roundTrip(db, model, "native", fixtures.mixedDataset(model, "native", false));
        fixtures.assertMixed(output, "native", false);
        try (var sql = db.connect()) {
            var asset = db.table("asset");
            var row = " FROM " + asset + " WHERE t_ili_tid='native-value'";
            String identity = "SELECT CAST(t_id AS text) || ':' || CAST(t_basket AS text)";
            String before = scalar(sql, identity + row);
            assertEquals("entry", scalar(sql, "SELECT items1_t_type" + row));
            assertEquals("é & a", scalar(sql, "SELECT " + jsonKey(db, "doc", "Label") + row));
            assertEquals("high", scalar(sql, "SELECT " + jsonKey(db, "doc", "Code") + row));
            assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + jsonKey(db, "doc", "@type") + row));
            String quantity = db.postgis() ? "jsonb_typeof(doc->'Quantity')" : "json_type(doc,'$.Quantity')";
            String flag = db.postgis() ? "jsonb_typeof(doc->'Flag')" : "json_type(doc,'$.Flag')";
            assertEquals(db.postgis() ? "number" : "integer", scalar(sql, "SELECT " + quantity + row));
            assertEquals(db.postgis() ? "boolean" : "true", scalar(sql, "SELECT " + flag + row));
            assertNull(scalar(sql, "SELECT doc FROM " + asset + " WHERE t_ili_tid='native-absent'"));
            assertEquals(model + ".Data.Entry", scalar(sql, "SELECT " + jsonKey(db, "doc", "@type")
                    + " FROM " + asset + " WHERE t_ili_tid='native-empty-doc'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("entry")));
            execute(sql, "UPDATE " + asset + " SET doc='{\"@type\":\"Unknown\"}' WHERE t_ili_tid='native-value'");
            assertExportRejected(db, model, "native", output, "Unexpected JSON structure type Unknown");
            execute(sql, "UPDATE " + asset + " SET doc='" + (db.postgis() ? "[]" : "{") + "' WHERE t_ili_tid='native-value'");
            assertExportRejected(db, model, "native", output, "Invalid ili2db JSON");
            execute(sql, "UPDATE " + asset + " SET doc=NULL WHERE t_ili_tid='native-value'");

            assertCliSuccess(db, "update", model, "native", fixtures.mixedDataset(model, "native", true));
            assertEquals(before, scalar(sql, identity + row));
            assertNull(scalar(sql, "SELECT doc" + row));
            assertNull(scalar(sql, "SELECT items1_t_type" + row));
            assertNull(scalar(sql, "SELECT items1_label" + row));
            assertCliSuccess(db, "export", model, "native", output);
            fixtures.assertMixed(output, "native", true);
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}, XTF {1}]")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void nestedJsonAndEmptyStoredCollection(Backend backend, int version) throws Exception {
        var db = target(backend);
        var fixtures = new StructureMappingFixtures(work, version);
        String model = "JsonCollections" + version;
        assertSchemaWithIdentityColumns(db, model, fixtures.collectionsModel(model), "--coalesce-json");
        Path output = roundTrip(db, model, "native", fixtures.jsonCollectionDataset(model, "native", true));
        fixtures.assertJsonCollections(output, "native", true);
        try (var sql = db.connect()) {
            String table = db.table("asset");
            String detailType = db.postgis() ? "nest->'Detail'->>'@type'" : "json_extract(nest,'$.Detail.@type')";
            assertEquals(model + ".Data.Entry",
                    scalar(sql, "SELECT " + detailType + " FROM " + table + " WHERE t_ili_tid='native-many'"));
            execute(sql, "UPDATE " + table + " SET items='[]' WHERE t_ili_tid='native-one'");
        }
        assertCliSuccess(db, "export", model, "native", output);
        assertNull(child(fixtures.objects(output, "native").get("native-one"), "Items"));
    }

    private Target target(Backend backend) {
        return POSTGIS.target(work.resolve("mapping.gpkg"), backend);
    }

    private Path roundTrip(Target db, String model, String dataset, Path input) {
        Path output = work.resolve("out.xtf");
        assertCliSuccess(db, "import", model, dataset, input);
        assertCliSuccess(db, "export", model, dataset, output);
        return output;
    }
}
