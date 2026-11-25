package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static guru.interlis.db.DbTestSupport.execute;

final class IdentityColumnInteropTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @Test void geoPackage23() throws Exception { compare(23, null); }
    @Test void geoPackage24() throws Exception { compare(24, null); }
    @Test void postgis23() throws Exception { compare(23, POSTGIS.container()); }
    @Test void postgis24() throws Exception { compare(24, POSTGIS.container()); }

    private void compare(int version, PostgreSQLContainer postgres) throws Exception {
        var fixture = new IdentityColumnFixtures(work, version);
        var db = new TestDatabase(work, postgres, "identity", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        if (db.postgis()) {
            try (var sql = db.connect()) {
                execute(sql, "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
            }
        }
        compareProfile(reference, db, fixture, "newclass", false);
        compareProfile(reference, db, fixture, "newclass", true);
        if (version == 24) {
            compareProfile(reference, db, fixture, "smart1", true);
            compareProfile(reference, db, fixture, "smart2", true);
        }
    }

    private void compareProfile(ReferenceTool reference, TestDatabase db, IdentityColumnFixtures fixture,
                                String profile, boolean identityColumns) throws Exception {
        var name = profile + "_" + identityColumns;
        var legacy = db.peer("ref_" + name);
        var own = db.peer("own_" + name);
        var options = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--sqlEnableNull",
                "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056"));
        if (!profile.equals("newclass")) options.add("--" + profile + "Inheritance");
        if (identityColumns) options.addAll(List.of("--createBasketCol", "--createTidCol"));
        options.add(fixture.source.toString());
        reference.run(legacy, work.toString(), options.toArray(String[]::new));
        fixture.schema(own, profile, identityColumns, identityColumns);
        try (var expected = legacy.connect(); var actual = own.connect()) {
            DbSchemaCatalog.read(expected, legacy.postgis(), legacy.schema())
                    .assertSameAs(DbSchemaCatalog.read(actual, own.postgis(), own.schema()));
        }
        if (profile.equals("newclass")) fixture.roundTrip(legacy, name, profile, identityColumns, identityColumns);
    }
}
