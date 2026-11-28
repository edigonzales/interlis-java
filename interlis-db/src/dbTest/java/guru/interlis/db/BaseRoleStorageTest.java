package guru.interlis.db;

import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

final class BaseRoleStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsOneToOneRoleWhenProjectionDropsTarget(int version, Backend backend) throws Exception {
        var fixture = new BaseRoleFixtures(work, version);
        var db = POSTGIS.target(work.resolve("roles.gpkg"), backend);
        fixture.createExtension(db);
        assertCliSuccess(db, "import", fixture.extension, "roles", fixture.delivery);
        var output = work.resolve("base.xtf");
        assertCliSuccess(db, "export", fixture.extension, "roles", output, "--export-model", fixture.base);
        fixture.assertProjection(output, true);
        var published = Files.readAllBytes(output);

        assertCliSuccess(db, "replace", fixture.extension, "roles", fixture.deliveryWithDroppedMarker());
        assertCliFailure(db, "export", fixture.extension, "roles", output,
                "Missing projected target mark at " + fixture.base + ".Data.Assignment.Mark of object survey in basket work",
                "--export-model", fixture.base);
        assertArrayEquals(published, Files.readAllBytes(output));
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsAttributedRoleWhenProjectionDropsTarget(int version, Backend backend) throws Exception {
        var fixture = new BaseRoleFixtures(work, version);
        var db = POSTGIS.target(work.resolve("filing.gpkg"), backend);
        fixture.createExtension(db);
        assertCliSuccess(db, "import", fixture.extension, "roles", fixture.deliveryWithDroppedArchive());
        var output = work.resolve("base.xtf");
        assertCliSuccess(db, "export", fixture.extension, "roles", output);
        var published = Files.readAllBytes(output);

        assertCliFailure(db, "export", fixture.extension, "roles", output,
                "Missing projected target archive at " + fixture.base + ".Data.Filing.FiledAt of object detailed in basket work",
                "--export-model", fixture.base);
        assertArrayEquals(published, Files.readAllBytes(output));
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsAnAttributedLinkOnAClassWithSubtypes(int version, Backend backend) throws Exception {
        var fixture = new BaseRoleFixtures(work, version);
        Files.writeString(fixture.extensionModel, Files.readString(fixture.extensionModel).replace("  END Work;", """
                    CLASS LocalDocument EXTENDS %s.Data.Document = END LocalDocument;
                  END Work;
                """.formatted(fixture.base).stripTrailing()));
        var db = POSTGIS.target(work.resolve("local-documents.gpkg"), backend);
        assertCliFailure(db, "schemaimport", fixture.extension, null, fixture.extensionModel,
                "embedded association with attributes on an owner with subtypes at " + fixture.base + ".Data.Filing",
                "--create-basket-col", "--create-tid-col", "--model-file", fixture.baseModel.toString());
        assertMissingTarget(db);
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void preservesOrderedPositionsAndRejectsCorruption(int version, Backend backend) throws Exception {
        var fixture = new BaseRoleFixtures(work, version);
        var db = POSTGIS.target(work.resolve("ordered.gpkg"), backend);
        fixture.createExtension(db);
        assertCliSuccess(db, "import", fixture.extension, "roles", fixture.delivery);
        var output = work.resolve("base.xtf");
        assertCliSuccess(db, "export", fixture.extension, "roles", output, "--export-model", fixture.base);
        fixture.assertProjection(output, true);

        var target = POSTGIS.target(work.resolve("projected.gpkg"), backend);
        assertSchemaWithIdentityColumns(target, fixture.base, fixture.baseModel);
        assertCliSuccess(target, "import", fixture.base, "base", output);
        fixture.assertStoredPositions(target, true);

        var published = Files.readAllBytes(output);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("survey") + " SET afollows_t_seq=0 WHERE t_ili_tid='survey'");
        }
        assertCliFailure(db, "export", fixture.extension, "roles", output,
                "Expected a positive integer role position at " + fixture.base + ".Data.Route.Follows of object survey",
                "--export-model", fixture.base);
        assertArrayEquals(published, Files.readAllBytes(output));
    }
}
