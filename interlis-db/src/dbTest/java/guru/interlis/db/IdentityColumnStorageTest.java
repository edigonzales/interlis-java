package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class IdentityColumnStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void identityColumnCombinations(int version, Backend backend) throws Exception {
        var fixture = new IdentityColumnFixtures(work, version);
        var profiles = version == 24 ? List.of("newclass", "smart1", "smart2") : List.of("newclass");
        for (var profile : profiles) {
            for (boolean baskets : List.of(false, true)) {
                for (boolean tids : List.of(false, true)) {
                    var name = profile + "-" + baskets + "-" + tids;
                    var db = POSTGIS.target(work.resolve(name + ".gpkg"), backend);
                    fixture.schema(db, profile, baskets, tids);
                    var output = fixture.roundTrip(db, name, profile, baskets, tids);
                    if (!baskets && !tids && profile.equals("newclass")) {
                        var extended = POSTGIS.target(work.resolve("extended.gpkg"), backend);
                        fixture.schema(extended, profile, false, false);
                        appendModel(extended, version);
                        verifyRollback(extended, fixture);
                        assertCliSuccess(extended, "import", fixture.model, null, output);
                        try (var sql = extended.connect()) {
                            assertEquals("2", scalar(sql, "select count(*) from " + extended.table("marker")));
                            assertEquals("100", scalar(sql, "select t_id from " + extended.table("addition")));
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void concreteOidUnderAbstractAnyOid(Backend backend) throws Exception {
        checkAbstractOid(POSTGIS.target(work.resolve("abstract-oid.gpkg"), backend));
    }

    private void appendModel(Target db, int version) throws Exception {
        String name = "Extra" + version;
        var ili = Files.writeString(work.resolve(name + ".ili"), """
                INTERLIS 2.%s;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data = CLASS Addition = END Addition; END Data;
                END %s.
                """.formatted(version - 20, name, name));
        var result = cli(db, "schemaimport", name, null, ili, CancellationToken.none(), "--add-models");
        assertEquals(0, result.code(), result::diagnostics);
        try (var sql = db.connect()) {
            execute(sql, "insert into " + db.table("addition") + " (t_id) values (100)");
        }
    }

    private void verifyRollback(Target db, IdentityColumnFixtures fixture) throws Exception {
        var bad = Files.writeString(work.resolve("broken.xtf"), Files.readString(fixture.input).replace("next-basket", "source-basket"));
        var result = cli(db, "import", fixture.model, null, bad, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        try (var sql = db.connect()) {
            assertEquals("0", scalar(sql, "select count(*) from " + db.table("marker")));
            assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset")));
            assertEquals("0", scalar(sql, "select count(*) from " + db.table("t_ili2db_basket")));
        }
        assertCliFailure(db, "import", fixture.model, "named", bad, "require a schema created with --create-basket-col");
    }

    private void checkAbstractOid(Target db) throws Exception {
        var ili = Files.writeString(work.resolve("AbstractOid.ili"), """
                INTERLIS 2.4;
                MODEL AbstractOid (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS Root (ABSTRACT) = OID AS INTERLIS.ANYOID; END Root;
                    CLASS Leaf EXTENDS Root = OID AS INTERLIS.UUIDOID; END Leaf;
                  END Data;
                END AbstractOid.
                """);
        assertCliSuccess(db, "schemaimport", "AbstractOid", null, ili);
        try (var sql = db.connect()) {
            assertEquals("INTERLIS.UUIDOID", scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='root' and tag='ch.ehi.ili2db.oidDomain'"));
        }
    }

}
