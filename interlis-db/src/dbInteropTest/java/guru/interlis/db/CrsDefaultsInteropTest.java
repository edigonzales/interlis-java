package guru.interlis.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.CrsFixtures.assertStoredEpsg;
import static guru.interlis.db.CrsInteropSupport.assertCatalogs;
import static guru.interlis.db.CrsInteropSupport.createReference;
import static guru.interlis.db.CrsInteropSupport.referenceExport;
import static guru.interlis.db.CrsInteropSupport.referenceImport;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;

final class CrsDefaultsInteropTest {
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

    @Test void geoPackageLocalSrid23() throws Exception { localSrid(23); }
    @Test void geoPackageLocalSrid24() throws Exception { localSrid(24); }

    @Test void geoPackageStaticGeneric24() throws Exception { staticGeneric(null); }
    @Test void postgisStaticGeneric24() throws Exception { staticGeneric(POSTGIS.container()); }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        var db = new TestDatabase(work, postgres, "default_lv03_reference", "v" + version + "_");
        var reference = new ReferenceTool(work, db.postgis());
        var fixtures = new CrsFixtures(work, version);
        String lv03 = "DefaultCrs" + version + "_21781";
        var lv03Source = fixtures.defaultModel(lv03, 21781);
        var own = db.peer("default_lv03_native");
        createReference(reference, db, work, lv03Source, 21781);
        assertSchemaWithIdentityColumns(own, lv03, lv03Source, "--default-epsg", "21781");
        assertCatalogs(db, own);
        stationExchange(reference, db, fixtures, lv03, 21781);
        stationExchange(reference, own, fixtures, lv03, 21781);

        String wgs84 = "DefaultCrs" + version + "_4326";
        var wgs84Source = fixtures.defaultModel(wgs84, 4326);
        var wgs84Reference = db.peer("default_wgs84_reference");
        var wgs84Native = db.peer("default_wgs84_native");
        createReference(reference, wgs84Reference, work, wgs84Source, 4326);
        assertSchemaWithIdentityColumns(wgs84Native, wgs84, wgs84Source, "--default-epsg", "4326");
        assertCatalogs(wgs84Reference, wgs84Native);

        declared(reference, db.peer("declared_reference"), db.peer("declared_native"), fixtures, version);
        modelOverride(reference, db.peer("override_reference"), fixtures, version);
    }

    private void declared(ReferenceTool reference, TestDatabase old, TestDatabase own, CrsFixtures fixtures, int version)
            throws Exception {
        String model = "DeclaredCrs" + version;
        Path ili = fixtures.fixture(model);
        createReference(reference, old, work, ili, 2056);
        assertSchemaWithIdentityColumns(own, model, ili);
        try (var legacy = old.connect(); var nativeSql = own.connect()) {
            var catalog = DbSchemaCatalog.read(legacy, old.postgis(), old.schema());
            if (!old.postgis()) {
                catalog = catalog.expectGeoPackageGeometryExtension("route", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "outline", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "boundary", "CURVEPOLYGON");
                if (version == 24) catalog = catalog.expectGeoPackageGeometryExtension("network", "routes", "MULTICURVE");
            }
            catalog.assertSameAs(DbSchemaCatalog.read(nativeSql, own.postgis(), own.schema()));
        }
        for (var db : List.of(old, own)) {
            var output = mixed(reference, db, fixtures, model, fixtures.declaredObjects(model, "legacy"),
                    fixtures.declaredObjects(model, "own"));
            fixtures.assertDeclared(output.fromReference(), "legacy");
            fixtures.assertDeclared(output.fromNative(), "own");
            try (var sql = db.connect()) {
                for (String tid : List.of("legacy", "own")) fixtures.assertDeclaredSrs(db, sql, tid);
            }
        }
    }

    private void modelOverride(ReferenceTool reference, TestDatabase db, CrsFixtures fixtures, int version) throws Exception {
        String model = "OverrideCrs" + version;
        createReference(reference, db, work, fixtures.overrideModel(model), 2056, "--modelSrsCode", model + "=21781");
        var output = mixed(reference, db, fixtures, model, overrideObjects(fixtures, model, "legacy"),
                overrideObjects(fixtures, model, "own"));
        try (var sql = db.connect()) {
            for (String tid : List.of("legacy", "own")) {
                var wire = tid.equals("legacy") ? output.fromReference() : output.fromNative();
                var basket = fixtures.basket(wire, tid);
                CrsFixtures.assertPoint(fixtures.object(basket, tid + "-station"), "Location", "600100.125", "200200.375");
                CrsFixtures.assertPoint(fixtures.object(basket, tid + "-globe"), "Location", "7.125", "46.375");
                assertStoredEpsg(db, sql, "station", "location", tid + "-station", 21781);
                assertStoredEpsg(db, sql, "globe", "location", tid + "-globe", 4326);
            }
        }
    }

    private static String overrideObjects(CrsFixtures fixture, String model, String tid) {
        return fixture.station(model, tid + "-station", 21781)
                + fixture.object(model, "Globe", tid + "-globe", CrsFixtures.field("Location", fixture.point("7.125", "46.375")));
    }

    private void staticGeneric(PostgreSQLContainer postgres) throws Exception {
        var old = new TestDatabase(work, postgres, "static_context_reference");
        var own = old.peer("static_context_native");
        var reference = new ReferenceTool(work, old.postgis());
        var fixtures = new CrsFixtures(work, 24);
        String model = "StaticCrs24";
        Path ili = fixtures.staticModel(model);
        createReference(reference, old, work, ili, 2056);
        assertSchemaWithIdentityColumns(own, model, ili);
        assertCatalogs(old, own);
        for (var db : List.of(old, own)) {
            stationExchange(reference, db, fixtures, model, 21781);
        }
    }

    private void localSrid(int version) throws Exception {
        var db = new TestDatabase(work, null, "local_srid");
        var reference = new ReferenceTool(work, false);
        var fixtures = new CrsFixtures(work, version);
        String model = "LocalSrid" + version;
        createReference(reference, db, work, fixtures.defaultModel(model, 21781), 21781);
        try (var sql = db.connect()) {
            CrsFixtures.useLocalGeoPackageSrs(sql);
        }
        var output = mixed(reference, db, fixtures, model, fixtures.station(model, "legacy", 21781), fixtures.station(model, "own", 21781));
        fixtures.assertStation(output.fromReference(), "legacy", 21781);
        fixtures.assertStation(output.fromNative(), "own", 21781);
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "legacy", 21781);
            assertStoredEpsg(db, sql, "station", "location", "own", 921781);
        }
    }

    private void stationExchange(ReferenceTool reference, TestDatabase db, CrsFixtures fixtures, String model, int epsg)
            throws Exception {
        var output = mixed(reference, db, fixtures, model, fixtures.station(model, "legacy", epsg), fixtures.station(model, "own", epsg));
        fixtures.assertStation(output.fromReference(), "legacy", epsg);
        fixtures.assertStation(output.fromNative(), "own", epsg);
        try (var sql = db.connect()) {
            assertStoredEpsg(db, sql, "station", "location", "legacy", epsg);
            assertStoredEpsg(db, sql, "station", "location", "own", epsg);
        }
    }

    private Exports mixed(ReferenceTool reference, TestDatabase db, CrsFixtures fixtures, String model,
                          String legacyObjects, String ownObjects) throws Exception {
        referenceImport(reference, db, "legacy", fixtures.delivery(model, "basket-legacy", legacyObjects));
        var fromReference = work.resolve(db.schema() + "-native-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", fromReference);
        assertCliSuccess(db, "import", model, "own", fixtures.delivery(model, "basket-own", ownObjects));
        var fromNative = work.resolve(db.schema() + "-reference-export.xtf");
        referenceExport(reference, db, "own", fromNative);
        return new Exports(fromReference, fromNative);
    }

    private record Exports(Path fromReference, Path fromNative) { }
}
