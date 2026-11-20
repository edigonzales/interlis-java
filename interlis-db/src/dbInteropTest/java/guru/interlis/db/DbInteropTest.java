package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import guru.interlis.execution.OperationCancelledException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DbInteropTest {
    private static final String XTF24_NS = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    private static final String STORED_MODELS = "%ILI_FROM_DB";
    private static final int POINT = 1;
    private static final int LINESTRING = 2;
    private static final int MULTIPOINT = 4;
    private static final int CIRCULARSTRING = 8;
    private static final int COMPOUNDCURVE = 9;
    private static final int CURVEPOLYGON = 10;
    private static final int MULTICURVE = 11;
    private static final int MULTISURFACE = 12;
    private static final List<List<String>> SURVEY_POINTS = List.of(
            List.of("2601500.625", "1201500.500"), List.of("2601100.125", "1201100.750"));
    private static final List<CurveComponent> STRAIGHT_ROUTE = List.of(new CurveComponent(LINESTRING, List.of(
            List.of("2600123.125", "1200456.375"), List.of("2600345.250", "1200678.500"),
            List.of("2600567.625", "1200456.750"))));
    private static final List<CurveComponent> ARC_ROUTE = List.of(new CurveComponent(CIRCULARSTRING, List.of(
            List.of("2600500.125", "1200500.250"), List.of("2600505.125", "1200505.250"),
            List.of("2600510.125", "1200500.250"), List.of("2600515.125", "1200495.250"),
            List.of("2600520.125", "1200500.250"))));
    private static final List<CurveComponent> MIXED_ROUTE = List.of(
            new CurveComponent(LINESTRING, List.of(
                    List.of("2600600.500", "1200600.250"), List.of("2600610.500", "1200600.250"))),
            new CurveComponent(CIRCULARSTRING, List.of(
                    List.of("2600610.500", "1200600.250"), List.of("2600615.500", "1200605.250"),
                    List.of("2600620.500", "1200600.250"))),
            new CurveComponent(LINESTRING, List.of(
                    List.of("2600620.500", "1200600.250"), List.of("2600630.500", "1200600.250"))));
    private static final List<List<CurveComponent>> SURFACE_WITH_HOLE = List.of(
            List.of(new CurveComponent(LINESTRING, List.of(
                    List.of("2601000.125", "1201000.375"), List.of("2601040.250", "1201000.375"),
                    List.of("2601040.250", "1201040.500"), List.of("2601000.125", "1201040.500"),
                    List.of("2601000.125", "1201000.375")))),
            List.of(new CurveComponent(LINESTRING, List.of(
                    List.of("2601008.125", "1201010.375"), List.of("2601008.125", "1201028.625"),
                    List.of("2601020.250", "1201020.500"), List.of("2601008.125", "1201010.375")))));
    private static final List<List<CurveComponent>> CURVED_SURFACE = List.of(List.of(
            new CurveComponent(CIRCULARSTRING, List.of(
                    List.of("2601200.250", "1201200.625"), List.of("2601210.250", "1201190.625"),
                    List.of("2601220.250", "1201200.625"))),
            new CurveComponent(LINESTRING, List.of(
                    List.of("2601220.250", "1201200.625"), List.of("2601200.250", "1201200.625")))));
    // The two parcel rings traverse their common edge in opposite directions.
    private static final List<List<CurveComponent>> WEST_AREA = List.of(List.of(new CurveComponent(LINESTRING, List.of(
            List.of("2602000.125", "1202000.250"), List.of("2602020.250", "1202000.250"),
            List.of("2602020.250", "1202020.750"), List.of("2602000.125", "1202020.750"),
            List.of("2602000.125", "1202000.250")))));
    private static final List<List<CurveComponent>> EAST_AREA = List.of(List.of(new CurveComponent(LINESTRING, List.of(
            List.of("2602020.250", "1202000.250"), List.of("2602040.500", "1202000.250"),
            List.of("2602040.500", "1202020.750"), List.of("2602020.250", "1202020.750"),
            List.of("2602020.250", "1202000.250")))));

    @TempDir Path work;

    @Test void geoPackage23() throws Exception { exchange(23, null); }
    @Test void geoPackage24() throws Exception { exchange(24, null); }
    @Test void postgis23() throws Exception { exchangeWithPostgis(23); }
    @Test void postgis24() throws Exception { exchangeWithPostgis(24); }

    private void exchangeWithPostgis(int version) throws Exception {
        var image = DockerImageName.parse("sogis/postgis:16-3.4").asCompatibleSubstituteFor("postgres");
        try (var postgres = new PostgreSQLContainer(image)
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=256m"))
                .withPassword("db_interop_password")) {
            postgres.start();
            exchange(version, postgres);
        }
    }

    private void exchange(int version, PostgreSQLContainer postgres) throws Exception {
        String model = "DbInterop" + version;
        Path ili = work.resolve(model + ".ili");
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + ili.getFileName())) {
            Files.copy(resource, ili);
        }
        String fixture;
        try (var resource = DbInteropTest.class.getResourceAsStream("/" + model + ".xtf")) {
            fixture = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }

        var reference = new ReferenceTool(postgres != null);
        var db = new TestDatabase(postgres, "reference");
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                ili.toString());
        try (var sql = db.connect()) {
            assertTypeKind(db, sql, "parcel", "boundary", "AREA");
            if (version == 24) {
                assertTypeKind(db, sql, "park", "coverage", "MULTISURFACE");
                assertTypeKind(db, sql, "district", "coverage", "MULTIAREA");
                assertTypeKind(db, sql, "routegroup", "traces", "MULTIPOLYLINE");
                assertTypeKind(db, sql, "survey", "locations", "MULTICOORD");
            }
            var catalog = DbSchemaCatalog.read(sql, db.postgis, db.schema);
            if (!db.postgis) {
                catalog = catalog.expectGeoPackageGeometryExtension("route", "trace", "COMPOUNDCURVE")
                        .expectGeoPackageGeometryExtension("plot", "footprint", "CURVEPOLYGON")
                        .expectGeoPackageGeometryExtension("parcel", "boundary", "CURVEPOLYGON");
                if (version == 24) {
                    catalog = catalog.expectGeoPackageGeometryExtension("park", "coverage", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("district", "coverage", "MULTISURFACE")
                            .expectGeoPackageGeometryExtension("routegroup", "traces", "MULTICURVE");
                }
            }
            assertSchemaCreation(reference, postgres, model, version, ili, fixture, catalog);
            Path nativeOutput = exchangeDatasets(reference, db, sql, work.toString(), model, version, fixture);
            assertRollback(db, sql, model, version, fixture);
            assertExplicitRadiusRollback(db, sql, model, version, fixture);
            assertOpenSurfaceRollback(db, sql, model, version, fixture);
            if (version == 24) {
                assertEmptyCollectionRollback(db, sql, model, fixture, "multisurface", "Park.Coverage", "multisurface", 13);
                assertEmptyCollectionRollback(db, sql, model, fixture, "multipolyline", "RouteGroup.Traces", "multicurve", 15);
                assertEmptyCollectionRollback(db, sql, model, fixture, "multicoord", "Survey.Locations", "multipoint", 16);
                assertCorruptNewCollectionRejected(db, sql, model, nativeOutput);
                assertEmptyCollectionExport(db, sql, model);
                assertCorruptMultiSurfaceRejected(db, sql, model, nativeOutput);
            }
            assertCorruptGeometriesRejected(db, sql, model, nativeOutput);
            execute(sql, "update " + db.table("t_ili2db_attrname") + " set sqlname='missing_select'"
                    + " where iliname='" + model + ".Data.Place.Select'");
            String originalOutput = Files.readString(nativeOutput);
            var corrupt = cli(db, "export", model, "legacy", nativeOutput, CancellationToken.none());
            assertNotEquals(0, corrupt.code(), corrupt::diagnostics);
            assertTrue(corrupt.diagnostics().contains("Select") || corrupt.diagnostics().contains("missing_select"),
                    corrupt::diagnostics);
            assertEquals(originalOutput, Files.readString(nativeOutput));
        }
    }

    private static void assertTypeKind(TestDatabase db, Connection sql, String table, String column, String kind)
            throws SQLException {
        assertEquals(kind, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='" + table + "' and columnname='" + column + "' and tag='ch.ehi.ili2db.typeKind'"));
    }

    private static void assertCorruptGeometriesRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String corruptLine = db.postgis
                ? "st_geomfromtext('COMPOUNDCURVE EMPTY', 2056)"
                : "X'4750000200000808'";
        execute(sql, "update " + db.table("route") + " set trace=" + corruptLine + " where t_ili_tid='tail-route-1'");
        assertExportRejected(db, model, "tail", output, model + ".Data.Route.Trace");

        String corruptSurface = db.postgis
                ? "st_geomfromtext('CURVEPOLYGON EMPTY', 2056)"
                : "X'4750000000000808000000000A00000001'";
        execute(sql, "update " + db.table("plot") + " set footprint=" + corruptSurface
                + " where t_ili_tid='native-plot-1'");
        assertExportRejected(db, model, "native", output, model + ".Data.Plot.Footprint");
    }

    private static void assertCorruptMultiSurfaceRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        String corrupt = db.postgis
                ? "st_geomfromtext('MULTISURFACE(CURVEPOLYGON((0 0,10 0,0 10,0 0)),CURVEPOLYGON EMPTY)', 2056)"
                : "X'4750000000000808000000000C00000001'";
        String park = db.table("park");
        String row = " where t_ili_tid='legacy-park-1'";
        execute(sql, "update " + park + " set coverage=" + corrupt + row);
        if (db.postgis) {
            assertEquals("2", scalar(sql, "select st_numgeometries(coverage) from " + park + row));
            assertEquals("true", scalar(sql, "select cast(st_isempty(st_geometryn(coverage, 2)) as text) from "
                    + park + row));
        }
        assertExportRejected(db, model, "legacy", output, model + ".Data.Park.Coverage");
    }

    private static void assertCorruptNewCollectionRejected(TestDatabase db, Connection sql, String model, Path output)
            throws Exception {
        if (db.postgis) {
            String survey = db.table("survey");
            String row = " where t_ili_tid='legacy-survey-1'";
            execute(sql, "update " + survey
                    + " set locations=st_geomfromtext('MULTIPOINT((2601500.625 1201500.500),EMPTY)', 2056)" + row);
            assertEquals("2", scalar(sql, "select st_numgeometries(locations) from " + survey + row));
            assertEquals("true", scalar(sql, "select cast(st_isempty(st_geometryn(locations, 2)) as text) from "
                    + survey + row));
            assertExportRejected(db, model, "legacy", output, model + ".Data.Survey.Locations");
        } else {
            execute(sql, "update routegroup set traces=X'4750000000000808000000000B00000001'"
                    + " where t_ili_tid='legacy-routegroup-1'");
            assertExportRejected(db, model, "legacy", output, model + ".Data.RouteGroup.Traces");
        }
    }

    private static void assertExportRejected(TestDatabase db, String model, String dataset, Path output, String attribute)
            throws Exception {
        String original = Files.readString(output);
        var rejected = cli(db, "export", model, dataset, output, CancellationToken.none());
        assertNotEquals(0, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(attribute), rejected::diagnostics);
        assertEquals(original, Files.readString(output));
    }

    private void assertSchemaCreation(ReferenceTool reference, PostgreSQLContainer postgres, String model, int version,
                                      Path ili, String fixture, DbSchemaCatalog catalog) throws Exception {
        boolean postgis = postgres != null;
        var db = new TestDatabase(postgres, "native");
        CliResult creation = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
        assertEquals(0, creation.code(), creation::diagnostics);
        try (var sql = db.connect()) {
            catalog.assertSameAs(DbSchemaCatalog.read(sql, postgis, db.schema));
            var duplicate = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
            assertNotEquals(0, duplicate.code(), duplicate::diagnostics);
            catalog.assertSameAs(DbSchemaCatalog.read(sql, postgis, db.schema));
            assertEmptySchema(sql, db, version);

            // Only the stored model is available under its recorded filename in this direction.
            Path heldModel = Files.move(ili, work.resolve("held-model"));
            try {
                exchangeDatasets(reference, db, sql, STORED_MODELS, model, version, fixture);
            } finally {
                Files.move(heldModel, ili);
            }
        }

        var invalidDb = new TestDatabase(postgres, "invalid");
        Path invalid = Files.writeString(work.resolve("invalid.ili"), "INTERLIS 2.4; MODEL");
        var failure = cli(invalidDb, "schemaimport", model, null, invalid, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        assertMissingTarget(invalidDb);

        assertExistingTargetPreserved(postgres, model, ili);
        var cancelledDb = new TestDatabase(postgres, "cancelled");
        var plan = SchemaPlan.read(model, ili, !postgis, CancellationToken.none());
        if (postgis) {
            try (var reserved = Database.openPostgres(cancelledDb.url, "select", cancelledDb.user,
                    cancelledDb.password, true)) {
                var rejected = assertThrows(DbException.class,
                        () -> SchemaSql.createPostgres(reserved, plan, CancellationToken.none()));
                assertTrue(rejected.getMessage().contains("reserved word"), rejected::getMessage);
            }
            try (var target = Database.openPostgres(cancelledDb.url, cancelledDb.schema, cancelledDb.user,
                    cancelledDb.password, true)) {
                // The first execution checkpoint follows CREATE SCHEMA.
                assertThrows(OperationCancelledException.class,
                        () -> SchemaSql.createPostgres(target, plan, () -> true));
                assertEquals(cancelledDb.schema, scalar(target.connection(),
                        "select nspname from pg_namespace where nspname='" + cancelledDb.schema + "'"));
            }
        } else {
            int before = temporaryFiles();
            var checks = new AtomicInteger();
            // The fourth check precedes CREATE TABLE gpkg_spatial_ref_sys.
            assertThrows(OperationCancelledException.class,
                    () -> GeoPackageSchema.create(cancelledDb.file, plan, () -> checks.incrementAndGet() == 5));
            assertEquals(before, temporaryFiles());
        }
        assertMissingTarget(cancelledDb);
    }

    private int temporaryFiles() throws IOException {
        try (var files = Files.list(work)) {
            return Math.toIntExact(files.filter(file -> file.getFileName().toString().startsWith(".interlis-db-"))
                    .count());
        }
    }

    private void assertExistingTargetPreserved(PostgreSQLContainer postgres, String model, Path ili) throws Exception {
        var db = new TestDatabase(postgres, "occupied");
        if (db.postgis) {
            try (var sql = db.connect()) {
                execute(sql, "create schema " + db.schema);
                var failure = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
                assertNotEquals(0, failure.code(), failure::diagnostics);
                assertEquals(db.schema, scalar(sql, "select nspname from pg_namespace where nspname='"
                        + db.schema + "'"));
                assertEquals("0", scalar(sql, "select count(*) from information_schema.tables where table_schema='"
                        + db.schema + "'"));
            }
        } else {
            String original = "Existing unrelated file";
            Files.writeString(db.file, original);
            var failure = cli(db, "schemaimport", model, null, ili, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertEquals(original, Files.readString(db.file));
        }
    }

    private Path exchangeDatasets(ReferenceTool reference, TestDatabase db, Connection sql, String modelDir,
                                  String model, int version, String fixture) throws Exception {
        String selectMapping = " from " + db.table("t_ili2db_attrname")
                + " where iliname='" + model + ".Data.Place.Select'";
        assertEquals("aselect", scalar(sql, "select sqlname" + selectMapping));

        Path legacyInput = Files.writeString(work.resolve("legacy.xtf"), fixture);
        reference.run(db, modelDir, "--import", "--importTid", "--importBid", "--dataset", "legacy",
                legacyInput.toString());
        assertStoredDataset(db, sql, version, "legacy", false);
        // The reference normalizes empty TEXT to NULL. Restore an actual empty SQL value to test our export.
        execute(sql, "update " + db.table("place") + " set aselect='' where t_ili_tid='legacy-place-2'");
        Path nativeOutput = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", nativeOutput);
        assertTransfer(nativeOutput, version, "legacy", true);

        Path nativeInput = Files.writeString(work.resolve("native.xtf"), fixture.replace("legacy-", "native-"));
        assertCliSuccess(db, "import", model, "native", nativeInput);
        assertStoredDataset(db, sql, version, "native", true);
        Path legacyOutput = work.resolve("legacy-export.xtf");
        reference.run(db, modelDir, "--export", "--exportTid", "--dataset", "native", legacyOutput.toString());
        assertTransfer(legacyOutput, version, "native", false);

        if (db.postgis && modelDir.equals(STORED_MODELS)) assertPostgresIdDefault(db);
        Path tail = Files.writeString(work.resolve("tail.xtf"), fixture.replace("legacy-", "tail-"));
        reference.run(db, modelDir, "--import", "--importTid", "--importBid", "--dataset", "tail", tail.toString());
        assertStoredDataset(db, sql, version, "tail", false);
        assertRows(db, sql, version, 3);
        return nativeOutput;
    }

    private static void assertPostgresIdDefault(TestDatabase db) throws SQLException {
        try (var sql = db.connect()) {
            sql.setAutoCommit(false);
            long lastId = Long.parseLong(scalar(sql, "select last_value from " + db.table("t_ili2db_seq")));
            String newId = scalar(sql, "insert into " + db.table("place") + " (t_basket) values ((select min(t_id) from "
                    + db.table("t_ili2db_basket") + ")) returning t_id");
            assertEquals(Long.toString(lastId + 1), newId);
            sql.rollback();
        }
    }

    private static void assertEmptySchema(Connection sql, TestDatabase db, int version) throws SQLException {
        assertRows(db, sql, version, 0);
        if (db.postgis) {
            assertEquals("false", scalar(sql, "select cast(is_called as text) from " + db.table("t_ili2db_seq")));
        } else {
            assertEquals("0", scalar(sql, "select count(*) from t_key_object"));
        }
    }

    private static void assertMissingTarget(TestDatabase db) throws SQLException {
        if (db.postgis) {
            try (var sql = db.connect()) {
                assertNull(scalar(sql, "select nspname from pg_namespace where nspname='" + db.schema + "'"));
            }
        } else {
            assertFalse(Files.exists(db.file), db.file::toString);
        }
    }

    private void assertRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        Path broken = Files.writeString(work.resolve("rollback.xtf"), fixture.replace("legacy-", "fail-"));
        if (db.postgis) {
            String sequenceValue = "select last_value from " + db.table("t_ili2db_seq");
            long before = Long.parseLong(scalar(sql, sequenceValue));
            // Sequence increments are visible outside the transaction. The third allocation is for the
            // first Route object, after the dataset and basket. The reader checks cancellation between inserts.
            CancellationToken afterFirstObject = () -> {
                try {
                    return Long.parseLong(scalar(sql, sequenceValue)) >= before + 3;
                } catch (SQLException failure) {
                    throw new AssertionError(failure);
                }
            };
            var cancelled = cli(db, "import", model, "fail", broken, afterFirstObject);
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(Long.toString(before + 3), scalar(sql, sequenceValue));
        } else {
            // The trigger fires after the line and first place have both reached the transaction.
            execute(sql, """
                    create trigger fail_place before insert on place
                    when new.t_ili_tid='fail-place-2' and exists(select 1 from place where t_ili_tid='fail-place-1')
                    and exists(select 1 from route where t_ili_tid='fail-route-1' and trace is not null)
                    begin select raise(abort, 'rollback probe after line'); end
                    """);
            var rejected = cli(db, "import", model, "fail", broken, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("SQL code"), rejected::diagnostics);
        }
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='fail'"));
        assertRows(db, sql, version, 3);
    }

    private void assertExplicitRadiusRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // The first arc follows the complete straight route in the same basket.
        String closing = version == 24 ? "</g:arc>" : "</ARC>";
        String radius = version == 24 ? "<g:r>5</g:r>" : "<R>5</R>";
        Path input = Files.writeString(work.resolve("radius.xtf"),
                fixture.replace("legacy-", "radius-").replaceFirst(closing, radius + closing));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "radius", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.Route.Trace"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("Explicit arc radius"), rejected::diagnostics);
        // On PostGIS the dataset, basket, straight route and rejected route each allocate an ID.
        // GeoPackage rolls back its counter together with the rows.
        assertEquals(Long.toString(db.postgis ? before + 4 : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='radius'"));
        assertRows(db, sql, version, 3);
    }

    private void assertOpenSurfaceRollback(TestDatabase db, Connection sql, String model, int version, String fixture)
            throws Exception {
        // Move only the closing position of the second plot, after the first plot and other objects.
        String x = "2601200.250";
        int plot = fixture.indexOf("legacy-plot-2");
        int closing = fixture.lastIndexOf(x, fixture.indexOf("</Footprint>", plot));
        assertTrue(closing > fixture.indexOf(x, plot));
        String openRing = fixture.substring(0, closing) + "2601200.375" + fixture.substring(closing + x.length());
        Path input = Files.writeString(work.resolve("open-surface.xtf"), openRing.replace("legacy-", "open-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "open", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data.Plot.Footprint"), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("closed"), rejected::diagnostics);
        // PostgreSQL allocates IDs for the dataset, basket, initial objects and the rejected plot.
        long allocated = version == 24 ? 10 : 9;
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='open'"));
        assertRows(db, sql, version, 3);
    }

    private void assertEmptyCollectionRollback(TestDatabase db, Connection sql, String model, String fixture,
                                               String tag, String attribute, String kind, long allocated) throws Exception {
        int start = fixture.indexOf("<g:" + tag + ">");
        String endTag = "</g:" + tag + ">";
        int end = fixture.indexOf(endTag, start) + endTag.length();
        String empty = fixture.substring(0, start) + "<g:" + tag + "/>" + fixture.substring(end);
        Path input = Files.writeString(work.resolve("empty-" + tag + ".xtf"), empty.replace("legacy-", "empty-"));
        String counter = db.postgis ? "select last_value from " + db.table("t_ili2db_seq")
                : "select T_LastUniqueId from T_KEY_OBJECT where T_Key='T_Id'";
        long before = Long.parseLong(scalar(sql, counter));
        var rejected = cli(db, "import", model, "empty", input, CancellationToken.none());
        assertEquals(1, rejected.code(), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains(model + ".Data." + attribute), rejected::diagnostics);
        assertTrue(rejected.diagnostics().contains("empty " + kind), rejected::diagnostics);
        // PostgreSQL allocates IDs for the dataset, basket, initial objects and the rejected object.
        assertEquals(Long.toString(db.postgis ? before + allocated : before), scalar(sql, counter));
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='empty'"));
        assertRows(db, sql, 24, 3);
    }

    private void assertEmptyCollectionExport(TestDatabase db, Connection sql, String model) throws Exception {
        String emptySurface = emptyCollection(db, "MULTISURFACE", MULTISURFACE);
        for (String table : List.of("park", "district")) {
            execute(sql, "update " + db.table(table) + " set coverage=" + emptySurface
                    + " where t_ili_tid='legacy-" + table + "-1'");
        }
        execute(sql, "update " + db.table("routegroup") + " set traces=" + emptyCollection(db, "MULTICURVE", MULTICURVE)
                + " where t_ili_tid='legacy-routegroup-1'");
        execute(sql, "update " + db.table("survey") + " set locations=" + emptyCollection(db, "MULTIPOINT", MULTIPOINT)
                + " where t_ili_tid='legacy-survey-1'");
        Path output = work.resolve("empty-collection-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        var basket = children(readDataSection(output, 24)).stream()
                .filter(element -> id(element, 24, "bid").equals("legacy-basket-1")).findFirst().orElseThrow();
        var attributes = Map.of("Park", "Coverage", "District", "Coverage", "RouteGroup", "Traces", "Survey", "Locations");
        for (var attribute : attributes.entrySet()) {
            var object = child(basket, attribute.getKey());
            assertNotNull(object);
            assertEquals("legacy-" + attribute.getKey().toLowerCase(Locale.ROOT) + "-1", id(object, 24, "tid"));
            assertNull(child(object, attribute.getValue()));
        }
    }

    private static String emptyCollection(TestDatabase db, String geometryType, int wkbType) {
        return db.postgis ? "st_geomfromtext('" + geometryType + " EMPTY', 2056)"
                : "X'475000100000080800%08X00000000'".formatted(wkbType);
    }

    private static void assertStoredDataset(TestDatabase db, Connection sql, int version, String dataset, boolean nativeImport)
            throws SQLException {
        String point = db.postgis ? "st_srid(p.ageometry)" : "p.ageometry";
        String rows = "select p.t_ili_tid, b.t_ili_tid, p.aselect, " + point + " from " + db.table("place") + " p"
                + " join " + db.table("t_ili2db_basket") + " b on p.t_basket=b.t_id"
                + " join " + db.table("t_ili2db_dataset") + " d on b.dataset=d.t_id"
                + " where d.datasetname='" + dataset + "' order by p.t_ili_tid";
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            assertTrue(result.next());
            assertEquals(dataset + "-place-1", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals("Brücke", result.getString(3));
            if (db.postgis) {
                srsId = result.getInt(4);
            } else {
                byte[] binary = result.getBytes(4);
                assertNotNull(binary);
                srsId = readGeoPackageHeader(ByteBuffer.wrap(binary));
            }
            assertTrue(result.next());
            assertEquals(dataset + "-place-2", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals(nativeImport ? "" : null, result.getString(3));
            assertNull(result.getObject(4));
            assertTrue(result.next());
            assertEquals(dataset + "-place-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getString(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, "place", "ageometry", srsId);
        assertStoredRoutes(db, sql, dataset, nativeImport);
        assertStoredSurfaces(db, sql, dataset, "plot", "footprint", SURFACE_WITH_HOLE, CURVED_SURFACE, nativeImport);
        assertStoredSurfaces(db, sql, dataset, "parcel", "boundary", WEST_AREA, EAST_AREA, nativeImport);
        if (version == 24) {
            for (String table : List.of("park", "district")) {
                assertStoredCollection(db, sql, dataset, table, "coverage", MULTISURFACE, nativeImport, geometry -> {
                    assertCurvePolygon(geometry, SURFACE_WITH_HOLE);
                    assertCurvePolygon(geometry, CURVED_SURFACE);
                });
            }
            assertStoredCollection(db, sql, dataset, "routegroup", "traces", MULTICURVE, nativeImport, geometry -> {
                assertCompoundCurve(geometry, STRAIGHT_ROUTE);
                assertCompoundCurve(geometry, MIXED_ROUTE);
            });
            assertStoredCollection(db, sql, dataset, "survey", "locations", MULTIPOINT, nativeImport, geometry -> {
                for (var surveyPoint : SURVEY_POINTS) {
                    assertEquals(POINT, readWkbType(geometry));
                    assertStoredPosition(geometry, surveyPoint);
                }
            });
        }
    }

    private static void assertStoredRoutes(TestDatabase db, Connection sql, String dataset, boolean nativeImport)
            throws SQLException {
        String rows = geometryRows(db, dataset, "route", "trace");
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstBasket = dataset + "-basket-1";
            srsId = assertStoredRoute(db, result, dataset + "-route-1", firstBasket, STRAIGHT_ROUTE, false);
            assertTrue(result.next());
            assertEquals(dataset + "-route-2", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertEquals(srsId, assertStoredRoute(db, result, dataset + "-route-3", firstBasket, ARC_ROUTE, nativeImport));
            assertEquals(srsId, assertStoredRoute(db, result, dataset + "-route-4", firstBasket, MIXED_ROUTE, nativeImport));
            assertFalse(result.next());
        }
        assertSrs(db, sql, "route", "trace", srsId);
    }

    private static int assertStoredRoute(TestDatabase db, ResultSet result, String tid, String bid,
                                         List<CurveComponent> components, boolean withoutEnvelope) throws SQLException {
        var geometry = readStoredGeometry(result, tid, bid);
        int srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
        if (!db.postgis && withoutEnvelope) assertEquals(8, geometry.position(), "Curved GeoPackage header length");
        assertCompoundCurve(geometry, components);
        assertFalse(geometry.hasRemaining());
        return srsId;
    }

    private static void assertStoredSurfaces(TestDatabase db, Connection sql, String dataset, String table, String column,
                                             List<List<CurveComponent>> firstRings,
                                             List<List<CurveComponent>> secondRings, boolean nativeImport)
            throws SQLException {
        String rows = geometryRows(db, dataset, table, column);
        String tidPrefix = dataset + "-" + table;
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            String firstBasket = dataset + "-basket-1";
            srsId = assertStoredSurface(db, result, tidPrefix + "-1", firstBasket, firstRings, nativeImport);
            assertEquals(srsId,
                    assertStoredSurface(db, result, tidPrefix + "-2", firstBasket, secondRings, nativeImport));
            assertTrue(result.next());
            assertEquals(tidPrefix + "-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, table, column, srsId);
    }

    private static int assertStoredSurface(TestDatabase db, ResultSet result, String tid, String bid,
                                          List<List<CurveComponent>> rings, boolean nativeImport) throws SQLException {
        var geometry = readStoredGeometry(result, tid, bid);
        int srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
        if (!db.postgis && nativeImport) assertEquals(8, geometry.position(), "Surface GeoPackage header length");
        assertCurvePolygon(geometry, rings);
        assertFalse(geometry.hasRemaining());
        return srsId;
    }

    private static void assertStoredCollection(TestDatabase db, Connection sql, String dataset, String table,
                                               String column, int type, boolean nativeImport,
                                               Consumer<ByteBuffer> assertMembers) throws SQLException {
        String rows = geometryRows(db, dataset, table, column);
        String tidPrefix = dataset + "-" + table;
        int srsId;
        try (var statement = sql.createStatement(); var result = statement.executeQuery(rows)) {
            var geometry = readStoredGeometry(result, tidPrefix + "-1", dataset + "-basket-1");
            srsId = db.postgis ? result.getInt(4) : readGeoPackageHeader(geometry);
            if (!db.postgis && nativeImport) assertEquals(8, geometry.position(), "Collection GeoPackage header length");
            assertEquals(type, readWkbType(geometry));
            assertEquals(2, geometry.getInt());
            assertMembers.accept(geometry);
            assertFalse(geometry.hasRemaining());
            assertTrue(result.next());
            assertEquals(tidPrefix + "-2", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getBytes(3));
            assertFalse(result.next());
        }
        assertSrs(db, sql, table, column, srsId);
    }

    private static String geometryRows(TestDatabase db, String dataset, String table, String column) {
        String field = "o." + column;
        String geometry = db.postgis ? "st_asbinary(" + field + "), st_srid(" + field + ")" : field;
        return "select o.t_ili_tid, b.t_ili_tid, " + geometry + " from " + db.table(table) + " o"
                + " join " + db.table("t_ili2db_basket") + " b on o.t_basket=b.t_id"
                + " join " + db.table("t_ili2db_dataset") + " d on b.dataset=d.t_id"
                + " where d.datasetname='" + dataset + "' order by o.t_ili_tid";
    }

    private static void assertCurvePolygon(ByteBuffer geometry, List<List<CurveComponent>> rings) {
        assertEquals(CURVEPOLYGON, readWkbType(geometry));
        assertEquals(rings.size(), geometry.getInt());
        for (var ring : rings) assertCompoundCurve(geometry, ring);
    }

    private static ByteBuffer readStoredGeometry(ResultSet result, String tid, String bid) throws SQLException {
        assertTrue(result.next());
        assertEquals(tid, result.getString(1));
        assertEquals(bid, result.getString(2));
        byte[] bytes = result.getBytes(3);
        assertNotNull(bytes);
        return ByteBuffer.wrap(bytes);
    }

    private static void assertCompoundCurve(ByteBuffer geometry, List<CurveComponent> components) {
        assertEquals(COMPOUNDCURVE, readWkbType(geometry));
        assertEquals(components.size(), geometry.getInt());
        for (var component : components) {
            assertEquals(component.type(), readWkbType(geometry));
            assertEquals(component.vertices().size(), geometry.getInt());
            for (var vertex : component.vertices()) {
                assertStoredPosition(geometry, vertex);
            }
        }
    }

    private static void assertStoredPosition(ByteBuffer geometry, List<String> position) {
        for (String ordinate : position) {
            assertEquals(Double.parseDouble(ordinate), geometry.getDouble());
        }
    }

    private static int readGeoPackageHeader(ByteBuffer bytes) {
        assertEquals('G', bytes.get());
        assertEquals('P', bytes.get());
        assertEquals(0, bytes.get());
        int flags = Byte.toUnsignedInt(bytes.get());
        assertTrue(flags <= 3, "Expected a nonempty standard 2D GeoPackage geometry");
        bytes.order((flags & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int srs = bytes.getInt();
        if ((flags & 2) != 0) bytes.position(bytes.position() + 4 * Double.BYTES);
        return srs;
    }

    private static int readWkbType(ByteBuffer bytes) {
        int order = bytes.get();
        assertTrue(order == 0 || order == 1, "Invalid WKB byte order");
        bytes.order(order == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        return bytes.getInt();
    }

    private static void assertSrs(TestDatabase db, Connection sql, String table, String column, int srsId)
            throws SQLException {
        if (db.postgis) {
            assertEquals(2056, srsId);
            return;
        }
        assertEquals("2056", scalar(sql, "select organization_coordsys_id from gpkg_spatial_ref_sys"
                + " where organization='EPSG' and srs_id=" + srsId));
        assertEquals(Integer.toString(srsId), scalar(sql, "select srs_id from gpkg_geometry_columns"
                + " where table_name='" + table + "' and column_name='" + column + "'"));
    }

    private static void assertRows(TestDatabase db, Connection sql, int version, int datasets) throws SQLException {
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("t_ili2db_basket")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("place")));
        assertEquals(Integer.toString(datasets * 4), scalar(sql, "select count(*) from " + db.table("route")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("plot")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("parcel")));
        var tables = new ArrayList<>(List.of("t_ili2db_dataset", "t_ili2db_basket", "place", "route", "plot", "parcel"));
        if (version == 24) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("link")));
            var collections = List.of("park", "district", "routegroup", "survey");
            for (String table : collections) {
                assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table(table)));
            }
            tables.add("link");
            tables.addAll(collections);
        }
        var ids = tables.stream().map(table -> "select t_id from " + db.table(table)).toList();
        assertEquals(Integer.toString(datasets * (version == 24 ? 25 : 16)),
                scalar(sql, "select count(distinct t_id) from (" + String.join(" union all ", ids) + ") ids"));
    }

    private static Element readDataSection(Path output, int version) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(output.toFile());
        var data = child(document.getDocumentElement(), version == 24 ? "datasection" : "DATASECTION");
        assertNotNull(data);
        return data;
    }

    private static void assertTransfer(Path output, int version, String dataset, boolean hasEmptyText) throws Exception {
        var data = readDataSection(output, version);
        Map<String, Set<String>> baskets = new HashMap<>();
        Map<String, Element> objects = new HashMap<>();
        for (Element basket : children(data)) {
            assertEquals(version == 24 ? "Data" : "DbInterop23.Data", basket.getLocalName());
            String bid = id(basket, version, "bid");
            var tids = new java.util.HashSet<String>();
            for (Element object : children(basket)) {
                String tid = id(object, version, "tid");
                assertNull(objects.put(tid, object));
                tids.add(tid);
            }
            assertNull(baskets.put(bid, tids));
        }
        String first = dataset + "-place-1";
        String second = dataset + "-place-2";
        String third = dataset + "-place-3";
        String link = dataset + "-link";
        String line = dataset + "-route-1";
        String noLine = dataset + "-route-2";
        String arcs = dataset + "-route-3";
        String mixed = dataset + "-route-4";
        String plotWithHole = dataset + "-plot-1";
        String curvedPlot = dataset + "-plot-2";
        String noSurface = dataset + "-plot-3";
        String westArea = dataset + "-parcel-1";
        String eastArea = dataset + "-parcel-2";
        String noArea = dataset + "-parcel-3";
        var firstBasket = new HashSet<>(Set.of(first, second, line, arcs, mixed, plotWithHole, curvedPlot,
                westArea, eastArea));
        var secondBasket = new HashSet<>(Set.of(third, noLine, noSurface, noArea));
        if (version == 24) {
            firstBasket.add(link);
            for (String table : List.of("park", "district", "routegroup", "survey")) {
                firstBasket.add(dataset + "-" + table + "-1");
                secondBasket.add(dataset + "-" + table + "-2");
            }
        }
        assertEquals(Map.of(dataset + "-basket-1", firstBasket, dataset + "-basket-2", secondBasket), baskets);

        String placeName = version == 24 ? "Place" : "DbInterop23.Data.Place";
        for (String tid : List.of(first, second, third)) {
            assertEquals(placeName, objects.get(tid).getLocalName());
        }
        Element place = objects.get(first);
        assertEquals("Brücke", child(place, "Select").getTextContent());
        assertEquals("true", child(place, "Active").getTextContent());
        assertEquals("-7", child(place, "Count").getTextContent());
        assertEquals("approved", child(place, "Status").getTextContent());
        var coord = children(child(place, "Geometry")).getFirst();
        assertEquals(0, new BigDecimal("2600000.125").compareTo(new BigDecimal(children(coord).getFirst().getTextContent())));
        assertEquals(0, new BigDecimal("1200000.375").compareTo(new BigDecimal(children(coord).get(1).getTextContent())));
        Element empty = child(objects.get(second), "Select");
        if (hasEmptyText) {
            assertNotNull(empty);
            assertEquals("", empty.getTextContent());
        } else {
            assertNull(empty);
        }
        assertEquals("false", child(objects.get(second), "Active").getTextContent());
        assertEquals(List.of(), children(objects.get(third)));
        if (version == 24) {
            assertEquals("Link", objects.get(link).getLocalName());
            assertEquals(first, id(child(objects.get(link), "Target"), version, "ref"));
        }
        String routeName = version == 24 ? "Route" : "DbInterop23.Data.Route";
        for (String tid : List.of(line, noLine, arcs, mixed)) {
            assertEquals(routeName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noLine), "Trace"));
        assertTransferRoute(child(objects.get(line), "Trace"), version, STRAIGHT_ROUTE);
        assertTransferRoute(child(objects.get(arcs), "Trace"), version, ARC_ROUTE);
        assertTransferRoute(child(objects.get(mixed), "Trace"), version, MIXED_ROUTE);
        String plotName = version == 24 ? "Plot" : "DbInterop23.Data.Plot";
        for (String tid : List.of(plotWithHole, curvedPlot, noSurface)) {
            assertEquals(plotName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noSurface), "Footprint"));
        assertTransferSurface(child(objects.get(plotWithHole), "Footprint"), version, SURFACE_WITH_HOLE);
        assertTransferSurface(child(objects.get(curvedPlot), "Footprint"), version, CURVED_SURFACE);
        String parcelName = version == 24 ? "Parcel" : "DbInterop23.Data.Parcel";
        for (String tid : List.of(westArea, eastArea, noArea)) {
            assertEquals(parcelName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noArea), "Boundary"));
        assertTransferSurface(child(objects.get(westArea), "Boundary"), version, WEST_AREA);
        assertTransferSurface(child(objects.get(eastArea), "Boundary"), version, EAST_AREA);
        if (version == 24) {
            for (String name : List.of("Park", "District")) {
                var surfaces = assertTransferCollection(objects, dataset, name, "Coverage", "multisurface", "surface");
                assertTransferRings(surfaces.getFirst(), 24, SURFACE_WITH_HOLE);
                assertTransferRings(surfaces.getLast(), 24, CURVED_SURFACE);
            }
            var lines = assertTransferCollection(objects, dataset, "RouteGroup", "Traces", "multipolyline", "polyline");
            assertTransferPolyline(lines.getFirst(), 24, STRAIGHT_ROUTE);
            assertTransferPolyline(lines.getLast(), 24, MIXED_ROUTE);
            var points = assertTransferCollection(objects, dataset, "Survey", "Locations", "multicoord", "coord");
            for (int index = 0; index < points.size(); index++) {
                var point = points.get(index);
                assertEquals(2, children(point).size());
                assertTransferPosition(point, "c", SURVEY_POINTS.get(index));
            }
        }
    }

    private static void assertTransferRoute(Element trace, int version, List<CurveComponent> components) {
        assertNotNull(trace);
        assertTransferPolyline(child(trace, version == 24 ? "polyline" : "POLYLINE"), version, components);
    }

    private static void assertTransferSurface(Element footprint, int version, List<List<CurveComponent>> rings) {
        assertNotNull(footprint);
        var surface = child(footprint, version == 24 ? "surface" : "SURFACE");
        assertNotNull(surface);
        assertTransferRings(surface, version, rings);
    }

    private static List<Element> assertTransferCollection(Map<String, Element> objects, String dataset, String name,
                                                          String attribute, String collectionTag, String memberTag) {
        String tidPrefix = dataset + "-" + name.toLowerCase(Locale.ROOT);
        var populated = objects.get(tidPrefix + "-1");
        var absent = objects.get(tidPrefix + "-2");
        assertEquals(name, populated.getLocalName());
        assertEquals(name, absent.getLocalName());
        assertNull(child(absent, attribute));
        var value = child(populated, attribute);
        assertNotNull(value);
        var multi = child(value, collectionTag);
        assertNotNull(multi);
        assertEquals("http://www.interlis.ch/geometry/1.0", multi.getNamespaceURI());
        var members = children(multi);
        assertEquals(List.of(memberTag, memberTag), members.stream().map(Element::getLocalName).toList());
        return members;
    }

    private static void assertTransferRings(Element surface, int version, List<List<CurveComponent>> rings) {
        var boundaries = children(surface);
        assertEquals(rings.size(), boundaries.size());
        for (int index = 0; index < rings.size(); index++) {
            var boundary = boundaries.get(index);
            if (version == 24) {
                assertEquals(index == 0 ? "exterior" : "interior", boundary.getLocalName());
            } else {
                assertEquals("BOUNDARY", boundary.getLocalName());
            }
            var lines = children(boundary);
            assertEquals(1, lines.size());
            var line = lines.getFirst();
            assertEquals(version == 24 ? "polyline" : "POLYLINE", line.getLocalName());
            assertTransferPolyline(line, version, rings.get(index));
        }
    }

    private static void assertTransferPolyline(Element polyline, int version, List<CurveComponent> components) {
        assertNotNull(polyline);
        var segments = children(polyline);
        int expectedSize = 1 + components.stream().mapToInt(component ->
                (component.vertices().size() - 1) / (component.type() == CIRCULARSTRING ? 2 : 1)).sum();
        assertEquals(expectedSize, segments.size());
        String coord = version == 24 ? "coord" : "COORD";
        String arc = version == 24 ? "arc" : "ARC";
        String c = version == 24 ? "c" : "C";
        String a = version == 24 ? "a" : "A";
        assertEquals(coord, segments.getFirst().getLocalName());
        assertEquals(2, children(segments.getFirst()).size());
        assertTransferPosition(segments.getFirst(), c, components.getFirst().vertices().getFirst());
        int segmentIndex = 1;
        for (var component : components) {
            boolean circular = component.type() == CIRCULARSTRING;
            for (int vertex = 1; vertex < component.vertices().size(); vertex += circular ? 2 : 1) {
                var segment = segments.get(segmentIndex++);
                assertEquals(circular ? arc : coord, segment.getLocalName());
                assertEquals(circular ? 4 : 2, children(segment).size());
                if (circular) {
                    // ili2db's XTF 2.4 exporter writes the intermediate point first.
                    assertTransferPosition(segment, a, component.vertices().get(vertex));
                    assertTransferPosition(segment, c, component.vertices().get(vertex + 1));
                } else {
                    assertTransferPosition(segment, c, component.vertices().get(vertex));
                }
            }
        }
    }

    private static void assertTransferPosition(Element segment, String prefix, List<String> expected) {
        for (int axis = 0; axis < expected.size(); axis++) {
            String name = prefix + (axis + 1);
            var ordinate = child(segment, name);
            assertNotNull(ordinate, name);
            assertEquals(0, new BigDecimal(expected.get(axis)).compareTo(new BigDecimal(ordinate.getTextContent())), name);
        }
    }

    private record CurveComponent(int type, List<List<String>> vertices) { }

    private static List<Element> children(Element parent) {
        var elements = new ArrayList<Element>();
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) elements.add(child);
        }
        return elements;
    }

    private static Element child(Element parent, String name) {
        return children(parent).stream().filter(element -> element.getLocalName().equals(name)).findFirst().orElse(null);
    }

    private static String id(Element element, int version, String name) {
        return version == 24 ? element.getAttributeNS(XTF24_NS, name) : element.getAttribute(name.toUpperCase(Locale.ROOT));
    }

    private static String scalar(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement(); var result = statement.executeQuery(query)) {
            if (!result.next()) return null;
            String value = result.getString(1);
            assertFalse(result.next(), query);
            return value;
        }
    }

    private static void execute(Connection sql, String query) throws SQLException {
        try (var statement = sql.createStatement()) {
            statement.executeUpdate(query);
        }
    }

    private static void assertCliSuccess(TestDatabase db, String operation, String model, String dataset, Path path) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none());
        assertEquals(0, result.code(), result::diagnostics);
    }

    private static CliResult cli(TestDatabase db, String operation, String model, String dataset, Path path,
                                 CancellationToken token) {
        var diagnostics = new StringWriter();
        var print = new PrintWriter(diagnostics);
        var args = new ArrayList<>(List.of(operation, "--model", model));
        if (dataset != null) args.addAll(List.of("--dataset", dataset));
        args.addAll(db.nativeOptions());
        args.add(path.toString());
        int code = Main.run(args.toArray(String[]::new), print, print, db.password, token);
        return new CliResult(code, diagnostics.toString());
    }

    private record CliResult(int code, String diagnostics) {}

    private final class ReferenceTool {
        private final Path jar;
        private final Path home;
        private int runs;

        private ReferenceTool(boolean postgis) throws IOException {
            String version = System.getProperty("ili2db.reference.version");
            String directory = System.getProperty("ili2db.reference.dir");
            assertNotNull(version, "Gradle must provide ili2db.reference.version");
            assertNotNull(directory, "Gradle must provide ili2db.reference.dir");
            String name = (postgis ? "ili2pg" : "ili2gpkg") + "-" + version;
            Path archive = Path.of(directory).resolve(name + ".zip");
            Path dist = Files.createDirectory(work.resolve("reference"));
            home = Files.createDirectory(work.resolve("reference-home"));
            try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
                for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    Path file = dist.resolve(entry.getName()).normalize();
                    if (!file.startsWith(dist)) throw new IOException("Invalid reference ZIP entry");
                    if (entry.isDirectory()) Files.createDirectories(file);
                    else {
                        Files.createDirectories(file.getParent());
                        Files.copy(zip, file);
                    }
                }
            }
            jar = dist.resolve(name + ".jar");
        }

        private void run(TestDatabase db, String modelDir, String... args) throws Exception {
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            var command = new ArrayList<>(List.of(java.toString(), "--enable-native-access=ALL-UNNAMED",
                    "-Duser.home=" + home, "-jar", jar.toString(), "--modeldir", modelDir));
            command.addAll(db.referenceOptions());
            command.addAll(List.of(args));
            Path log = work.resolve("reference-" + ++runs + ".log");
            Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true)
                    .redirectOutput(log.toFile()).start();
            try {
                assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> "Reference timed out: " + args[0]);
                assertEquals(0, process.exitValue(), () -> logText(log, db.password));
            } finally {
                if (process.isAlive()) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private final class TestDatabase {
        private final PostgreSQLContainer postgres;
        private final boolean postgis;
        private final Path file;
        private final String url;
        private final String user;
        private final String password;
        private final String schema;

        private TestDatabase(PostgreSQLContainer postgres, String name) {
            this.postgres = postgres;
            postgis = postgres != null;
            file = work.resolve(name + ".gpkg");
            url = postgis ? postgres.getJdbcUrl() : "jdbc:sqlite:" + file;
            user = postgis ? postgres.getUsername() : "";
            password = postgis ? postgres.getPassword() : "";
            schema = "db_interop_" + name;
        }

        private Connection connect() throws SQLException {
            return DriverManager.getConnection(url, user, password);
        }

        private List<String> nativeOptions() {
            return postgis ? List.of("--pg-url", url, "--schema", schema, "--user", user)
                    : List.of("--gpkg", file.toString());
        }

        private List<String> referenceOptions() {
            if (!postgis) return List.of("--dbfile", file.toString());
            return List.of("--dbhost", postgres.getHost(), "--dbport", postgres.getFirstMappedPort().toString(),
                    "--dbdatabase", postgres.getDatabaseName(), "--dbschema", schema, "--dbusr", user, "--dbpwd", password);
        }

        private String table(String name) { return postgis ? schema + "." + name : name; }
    }

    private static String logText(Path log, String password) {
        try {
            String text = Files.readString(log);
            return password.isEmpty() ? text : text.replace(password, "[redacted]");
        } catch (IOException failure) {
            return log + ": " + failure.getMessage();
        }
    }
}
