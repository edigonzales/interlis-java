package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import guru.interlis.execution.CancellationToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.sql.Connection;

import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.emptyCollection;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StrokeMultiGeometryStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void omitsEmptyMultipolygonsOnExport(int version, Backend backend) throws Exception {
        var fixture = new StrokeMultiGeometryFixtures(work, version);
        var db = POSTGIS.target(work.resolve("stroke-collections.gpkg"), backend);
        fixture.schema(db);
        var imported = cli(db, "import", fixture.lines, "shapes", fixture.input, CancellationToken.none(), fixture.extraModels());
        assertEquals(0, imported.code(), imported::diagnostics);
        try (var sql = db.connect()) {
            storeEmptyPolygonCollection(db, sql, "landcover", "fixture-surfaces-empty", 21781);
            if (version == 24) {
                storeEmptyPolygonCollection(db, sql, "parcelgroup", "fixture-direct-surface-null", 2056);
                storeEmptyPolygonCollection(db, sql, "districtgroup", "fixture-direct-area-null", 2056);
            }
        }
        var output = work.resolve("shapes-export.xtf");
        var exported = cli(db, "export", fixture.lines, "shapes", output, CancellationToken.none(), fixture.extraModels());
        assertEquals(0, exported.code(), exported::diagnostics);
        fixture.assertDelivery(output, "fixture");
    }

    private static void storeEmptyPolygonCollection(Target db, Connection sql, String table, String tid, int srid) throws Exception {
        String target = db.table(table);
        String row = " WHERE t_ili_tid='" + tid + "'";
        execute(sql, "UPDATE " + target + " SET shape=" + emptyCollection(db, "MULTIPOLYGON", 6, srid) + row);
        String empty = db.postgis() ? "ST_IsEmpty(shape) AND ST_SRID(shape)=" + srid : "length(shape)=17";
        assertEquals("1", scalar(sql, "SELECT count(*) FROM " + target + row + " AND " + empty), table);
    }
}
