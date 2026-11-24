package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.CrsFixtures.assertStoredEpsg;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertExportRejected;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CrsDefaultsStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void defaultSettingsAndMissingCrs(Backend backend, int version) throws Exception {
        var fixture = new CrsFixtures(work, version);
        for (int epsg : List.of(21781, 4326)) {
            String model = "DefaultCrs" + version + "_" + epsg;
            var db = POSTGIS.target(work.resolve(model + ".gpkg"), backend);
            assertSchemaWithIdentityColumns(db, model, fixture.defaultModel(model, epsg), "--default-epsg", Integer.toString(epsg));
            Path output = roundTrip(db, fixture, model, "station", fixture.station(model, "station", epsg));
            fixture.assertStation(output, "station", epsg);
            try (var sql = db.connect()) {
                assertStoredEpsg(db, sql, "station", "location", "station", epsg);
                execute(sql, "UPDATE " + db.table("t_ili2db_settings")
                        + " SET setting='2056' WHERE tag='ch.ehi.ili2db.defaultSrsCode'");
            }
            byte[] before = Files.readAllBytes(output);
            var failed = cli(db, "export", model, "station", output, CancellationToken.none());
            assertEquals(1, failed.code(), failed::diagnostics);
            assertTrue(failed.diagnostics().contains("Expected an EPSG:2056"), failed::diagnostics);
            assertTrue(failed.diagnostics().contains("station.location"), failed::diagnostics);
            assertArrayEquals(before, Files.readAllBytes(output));
        }

        String model = "MissingCrs" + version;
        var absent = POSTGIS.target(work.resolve("missing.gpkg"), backend);
        var result = schemaWithIdentityColumns(absent, model, fixture.defaultModel(model, 4326), CancellationToken.none(),
                "--default-epsg", "999999");
        assertEquals(1, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("missing the EPSG:999999 SRS"), result::diagnostics);
        assertMissingTarget(absent);
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void declaredCrsSurvivesRemovalOfSchemaDefault(Backend backend, int version) throws Exception {
        var fixture = new CrsFixtures(work, version);
        var db = POSTGIS.target(work.resolve("declared.gpkg"), backend);
        String model = "DeclaredCrs" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.fixture(model));
        assertCliSuccess(db, "import", model, "declared",
                fixture.delivery(model, "basket-declared", fixture.declaredObjects(model, "declared")));
        try (var sql = db.connect()) {
            fixture.assertDeclaredSrs(db, sql, "declared");
            execute(sql, "DELETE FROM " + db.table("t_ili2db_settings") + " WHERE tag='ch.ehi.ili2db.defaultSrsCode'");
        }
        Path output = work.resolve("declared.xtf");
        assertCliSuccess(db, "export", model, "declared", output);
        fixture.assertDeclared(output, "declared");
    }

    @ParameterizedTest(name = "{0}, XTF 24")
    @EnumSource(Backend.class)
    void staticGenericAssignment(Backend backend) throws Exception {
        var fixture = new CrsFixtures(work, 24);
        var db = POSTGIS.target(work.resolve("static-generic.gpkg"), backend);
        String model = "StaticCrs24";
        assertSchemaWithIdentityColumns(db, model, fixture.staticModel(model));
        Path output = roundTrip(db, fixture, model, "static", fixture.station(model, "static", 21781));
        fixture.assertStation(output, "static", 21781);
        String selection = model + ".Grid=" + model + ".LocalGrid";
        assertEquals(selection, id(fixture.basket(output, "static"), 24, "domains"));
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "static", 21781);
        }
        assertCliSuccess(db, "import", model, "explicit", output);
        var invalid = Files.writeString(work.resolve("invalid.xtf"),
                Files.readString(output).replace(selection, model + ".Grid=" + model + ".Grid"));
        assertCliFailure(db, "import", model, "invalid", invalid, "permitted assignments");
        try (var sql = db.connect()) {
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("station")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    @ParameterizedTest(name = "GEOPACKAGE, XTF {0}")
    @ValueSource(ints = {23, 24})
    void competingGeoPackageIdRejectsLegacyHeader(int version) throws Exception {
        var fixture = new CrsFixtures(work, version);
        var db = POSTGIS.target(work.resolve("local-srs.gpkg"), Backend.GEOPACKAGE);
        String model = "LocalSrid" + version;
        assertSchemaWithIdentityColumns(db, model, fixture.defaultModel(model, 21781), "--default-epsg", "21781");
        Path legacy = roundTrip(db, fixture, model, "legacy", fixture.station(model, "legacy", 21781));
        try (var sql = db.connect()) {
            CrsFixtures.useLocalGeoPackageSrs(sql);
            execute(sql, "UPDATE " + db.table("t_ili2db_settings")
                    + " SET setting='ili2gpkg-5.5.2' WHERE tag='ch.ehi.ili2db.sender'");
        }
        assertCliSuccess(db, "export", model, "legacy", legacy);
        fixture.assertStation(legacy, "legacy", 21781);
        Path own = roundTrip(db, fixture, model, "own", fixture.station(model, "own", 21781));

        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "legacy", 21781);
            assertStoredEpsg(db, sql, "station", "location", "own", 921781);
            execute(sql, """
                    INSERT INTO gpkg_spatial_ref_sys (srs_name, srs_id, organization, organization_coordsys_id, definition)
                    SELECT srs_name, 21781, organization, organization_coordsys_id, definition
                    FROM gpkg_spatial_ref_sys WHERE srs_id=4326
                    """);
        }
        assertExportRejected(db, model, "legacy", legacy, "SRS differs from its geometry column");
        assertCliSuccess(db, "export", model, "own", own);
        fixture.assertStation(own, "own", 21781);
    }

    @ParameterizedTest(name = "POSTGIS, XTF {0}")
    @ValueSource(ints = {23, 24})
    void changedPostgisCatalogRejectsExport(int version) throws Exception {
        // spatial_ref_sys is shared by all schemas in a database.
        try (var isolated = new PostgisTestServer()) {
            var fixture = new CrsFixtures(work, version);
            var db = isolated.target(work.resolve("unused.gpkg"));
            String model = "LocalSrid" + version;
            assertSchemaWithIdentityColumns(db, model, fixture.defaultModel(model, 21781), "--default-epsg", "21781");
            Path output = roundTrip(db, fixture, model, "station", fixture.station(model, "station", 21781));
            try (var sql = db.connect()) {
                assertStoredEpsg(db, sql, "station", "location", "station", 21781);
                execute(sql, "UPDATE spatial_ref_sys SET srid=921781 WHERE srid=21781");
            }
            assertExportRejected(db, model, "station", output, "Expected an EPSG:21781");
        }
    }

    private Path roundTrip(Target db, CrsFixtures fixture, String model, String tid, String objects) throws Exception {
        Path input = fixture.delivery(model, "basket-" + tid, objects);
        Path output = work.resolve(model + "-" + tid + "-out.xtf");
        assertCliSuccess(db, "import", model, tid, input);
        assertCliSuccess(db, "export", model, tid, output);
        return output;
    }
}
