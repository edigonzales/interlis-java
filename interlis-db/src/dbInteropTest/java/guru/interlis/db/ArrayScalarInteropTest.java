package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static guru.interlis.db.ArrayFixtures.objects;
import static guru.interlis.db.ArrayFixtures.withPrefix;
import static guru.interlis.db.ArrayFixtures.wrappedItems;
import static guru.interlis.db.ArrayScalarFixtures.GRADES;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.InteropCommands.assertSharedIds;
import static guru.interlis.db.InteropCommands.exportDataset;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ArrayScalarInteropTest {
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
        var fixture = new ArrayScalarFixtures(work, version);
        var baseline = new TestDatabase(work, postgres, "array_scalar_reference", "v" + version + "_");
        var own = baseline.peer("array_scalar_native");
        var reference = new ReferenceTool(work, baseline.postgis());
        schema(reference, baseline, work, fixture.model, "newClass", "--coalesceArray", "--expandStruct", "--coalesceJson");
        fixture.schema(own);
        try (var expected = baseline.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }

        Path input = withPrefix(work, fixture.input, "native");
        for (var db : List.of(baseline, own)) {
            importDataset(reference, db, "legacy", fixture.input);
            Path exported = work.resolve(db.schema() + "-legacy.xtf");
            assertCliSuccess(db, "export", fixture.model, "legacy", exported);
            fixture.assertDelivery(exported, "fixture", false);

            assertCliSuccess(db, "import", fixture.model, "native", input);
            exported = work.resolve(db.schema() + "-native.xtf");
            exportDataset(reference, db, "native", exported);
            fixture.assertDelivery(exported, "native", true);
            try (var sql = db.connect()) {
                String grades = fixture.wrappedGrades.toLowerCase(Locale.ROOT);
                String item = db.postgis() ? grades + "[1]" : "json_extract(" + grades + ", '$[0]')";
                assertEquals(GRADES.getFirst(), scalar(sql, "SELECT " + item + " FROM " + db.table("asset")
                        + " WHERE t_ili_tid='native-value'"));
                assertEquals(fixture.model + ".Data.Grade", scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                        + " WHERE tablename='asset' AND columnname='" + grades + "' AND subtype IS NULL"
                        + " AND tag='ch.ehi.ili2db.enumDomain'"));
            }
        }

        appendModel(reference, own, version);
        importDataset(reference, baseline, "null-values", fixture.nullInput());
        reference.expectFailure(baseline, "%ILI_FROM_DB", "illegal value null for Entry", "--export", "--dataset", "null-values",
                "--exportTid", work.resolve("null-reference.xtf").toString());
        Path output = work.resolve("null-native.xtf");
        assertCliSuccess(baseline, "export", fixture.model, "null-values", output);
        fixture.assertNullDelivery(output);
    }

    private void appendModel(ReferenceTool reference, TestDatabase own, int version) throws Exception {
        String model = "ArrayAppend" + version;
        var source = Files.writeString(work.resolve(model + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    STRUCTURE Elem = Entry : TEXT*40; END Elem;
                    CLASS Asset =
                      !!@ili2db.mapping=ARRAY
                      Items : LIST OF Elem;
                    END Asset;
                  END Data;
                END %s.
                """.formatted(version - 20, model, model));
        assertSchemaWithIdentityColumns(own, model, source, "--add-models");
        String qualifier = version == 23 ? model + ".Data." : "";
        String tid = version == 23 ? "TID" : "i:tid";
        String asset = qualifier + "Asset";
        String elem = qualifier + "Elem";
        String rows = "<%1$s %2$s=\"added-value\"><Items><%3$s><Entry>one</Entry></%3$s>"
                .formatted(asset, tid, elem) + "<%1$s><Entry>two</Entry></%1$s></Items></%2$s>".formatted(elem, asset);
        var input = Files.writeString(work.resolve("added-input.xtf"), referenceDatasetFixture(model, version, "added-b", rows));
        importDataset(reference, own, "added", input);
        var output = work.resolve("added-output.xtf");
        assertCliSuccess(own, "export", model, "added", output);
        assertEquals(List.of("one", "two"), wrappedItems(objects(output, version, "added").get("added-value"), "Items"));
        try (var sql = own.connect()) {
            assertEquals("coalesce", scalar(sql, "SELECT setting FROM " + own.table("t_ili2db_trafo")
                    + " WHERE iliname='" + model + ".Data.Asset.Items' AND tag='ch.ehi.ili2db.arrayTrafo'"));
        }
        assertSharedIds(own, 11, "asset", tableFor(own, model + ".Data.Asset"));
    }
}
