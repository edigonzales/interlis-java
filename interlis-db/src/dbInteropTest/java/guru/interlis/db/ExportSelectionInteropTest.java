package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.InteropCommands.importDataset;
import static guru.interlis.db.InteropCommands.schema;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ExportSelectionInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackageBaskets23() throws Exception { exchangeBaskets(23, null); }
    @Test void geoPackageBaskets24() throws Exception { exchangeBaskets(24, null); }
    @Test void postgisBaskets23() throws Exception { exchangeBaskets(23, POSTGIS.container()); }
    @Test void postgisBaskets24() throws Exception { exchangeBaskets(24, POSTGIS.container()); }

    private void exchangeBaskets(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new ExportSelectionFixtures(work, version);
        var source = new TestDatabase(work, postgres, "basket_export", "v" + version + "_");
        var reference = new ReferenceTool(work, source.postgis());
        schema(reference, source, work, fixture.model, "newClass");
        importDataset(reference, source, "first", fixture.delivery("first",
                fixture.basket("Data", "picked", fixture.asset("a", "selected", false)),
                fixture.basket("Data", "ignored", fixture.asset("b", "ignored", false))));
        assertCliSuccess(source, "import", fixture.model, "second", fixture.delivery("second",
                fixture.basket("Data", "unnamed", fixture.asset("c", "detached", false)),
                fixture.basket("Data", "picked-too", fixture.asset("d", "second", false))));
        try (var sql = source.connect()) {
            execute(sql, "UPDATE " + source.table("t_ili2db_basket") + " SET dataset=NULL WHERE t_ili_tid='unnamed'");
        }
        var nativeOutput = work.resolve("native-selection.xtf");
        var referenceOutput = work.resolve("reference-selection.xtf");
        assertCliSuccess(source, "export", fixture.model, null, nativeOutput,
                "--basket", "picked-too", "--basket", "unnamed", "--basket", "picked");
        reference.run(source, "%ILI_FROM_DB", "--export", "--baskets", "picked;unnamed;picked-too", "--exportTid",
                referenceOutput.toString());
        var expected = List.of("Data:picked:a:selected", "Data:unnamed:c:detached", "Data:picked-too:d:second");
        assertEquals(expected, fixture.exportedAssets(nativeOutput));
        assertEquals(Set.copyOf(expected), Set.copyOf(fixture.exportedAssets(referenceOutput)));
        var nativeTarget = source.peer("basket_native_import");
        var referenceTarget = source.peer("basket_reference_import");
        assertSchemaWithIdentityColumns(nativeTarget, fixture.model, fixture.source);
        assertSchemaWithIdentityColumns(referenceTarget, fixture.model, fixture.source);
        assertCliSuccess(nativeTarget, "import", fixture.model, "selected", referenceOutput);
        importDataset(reference, referenceTarget, "selected", nativeOutput);
        var expectedSql = Set.of(fixture.model + ".Data:picked:a:selected", fixture.model + ".Data:unnamed:c:detached",
                fixture.model + ".Data:picked-too:d:second");
        fixture.assertImportedAssets(nativeTarget, expectedSql);
        fixture.assertImportedAssets(referenceTarget, expectedSql);
    }
}
