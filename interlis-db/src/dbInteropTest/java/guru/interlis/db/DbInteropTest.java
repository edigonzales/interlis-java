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
            var catalog = DbSchemaCatalog.read(sql, db.postgis, db.schema);
            assertSchemaCreation(reference, postgres, model, version, ili, fixture, catalog);
            Path nativeOutput = exchangeDatasets(reference, db, sql, work.toString(), model, version, fixture);
            assertRollback(db, sql, model, version, fixture);
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
        assertStoredDataset(db, sql, "legacy", false);
        // The reference normalizes empty TEXT to NULL. Restore an actual empty SQL value to test our export.
        execute(sql, "update " + db.table("place") + " set aselect='' where t_ili_tid='legacy-place-2'");
        Path nativeOutput = work.resolve("native-export.xtf");
        assertCliSuccess(db, "export", model, "legacy", nativeOutput);
        assertTransfer(nativeOutput, version, "legacy", true);

        Path nativeInput = Files.writeString(work.resolve("native.xtf"), fixture.replace("legacy-", "native-"));
        assertCliSuccess(db, "import", model, "native", nativeInput);
        assertStoredDataset(db, sql, "native", true);
        Path legacyOutput = work.resolve("legacy-export.xtf");
        reference.run(db, modelDir, "--export", "--exportTid", "--dataset", "native", legacyOutput.toString());
        assertTransfer(legacyOutput, version, "native", false);

        if (db.postgis && modelDir.equals(STORED_MODELS)) assertPostgresIdDefault(db);
        Path tail = Files.writeString(work.resolve("tail.xtf"), fixture.replace("legacy-", "tail-"));
        reference.run(db, modelDir, "--import", "--importTid", "--importBid", "--dataset", "tail", tail.toString());
        assertStoredDataset(db, sql, "tail", false);
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
            // first object, after the dataset and basket. The reader checks cancellation between inserts.
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
            // The trigger fires only after the first object row has reached the same transaction.
            execute(sql, """
                    create trigger fail_place before insert on place
                    when new.t_ili_tid='fail-place-2' and exists(select 1 from place where t_ili_tid='fail-place-1')
                    begin select raise(abort, 'rollback probe after first row'); end
                    """);
            var rejected = cli(db, "import", model, "fail", broken, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("SQL code"), rejected::diagnostics);
        }
        assertNull(scalar(sql, "select datasetname from " + db.table("t_ili2db_dataset") + " where datasetname='fail'"));
        assertRows(db, sql, version, 3);
    }

    private static void assertStoredDataset(TestDatabase db, Connection sql, String dataset, boolean hasEmptyText)
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
                assertEquals('G', binary[0]);
                assertEquals('P', binary[1]);
                var order = (binary[3] & 1) == 0 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                srsId = ByteBuffer.wrap(binary).order(order).getInt(4);
            }
            assertTrue(result.next());
            assertEquals(dataset + "-place-2", result.getString(1));
            assertEquals(dataset + "-basket-1", result.getString(2));
            assertEquals(hasEmptyText ? "" : null, result.getString(3));
            assertNull(result.getObject(4));
            assertTrue(result.next());
            assertEquals(dataset + "-place-3", result.getString(1));
            assertEquals(dataset + "-basket-2", result.getString(2));
            assertNull(result.getString(3));
            assertFalse(result.next());
        }
        if (db.postgis) {
            assertEquals(2056, srsId);
        } else {
            assertEquals("2056", scalar(sql, "select organization_coordsys_id from gpkg_spatial_ref_sys"
                    + " where organization='EPSG' and srs_id=" + srsId));
            assertEquals(Integer.toString(srsId), scalar(sql, "select srs_id from gpkg_geometry_columns"
                    + " where table_name='place' and column_name='ageometry'"));
        }
    }

    private static void assertRows(TestDatabase db, Connection sql, int version, int datasets) throws SQLException {
        assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("t_ili2db_dataset")));
        assertEquals(Integer.toString(datasets * 2), scalar(sql, "select count(*) from " + db.table("t_ili2db_basket")));
        assertEquals(Integer.toString(datasets * 3), scalar(sql, "select count(*) from " + db.table("place")));
        var tables = new ArrayList<>(List.of("t_ili2db_dataset", "t_ili2db_basket", "place"));
        if (version == 24) {
            assertEquals(Integer.toString(datasets), scalar(sql, "select count(*) from " + db.table("link")));
            tables.add("link");
        }
        var ids = tables.stream().map(table -> "select t_id from " + db.table(table)).toList();
        assertEquals(Integer.toString(datasets * (version == 24 ? 7 : 6)),
                scalar(sql, "select count(distinct t_id) from (" + String.join(" union all ", ids) + ") ids"));
    }

    private static void assertTransfer(Path output, int version, String dataset, boolean hasEmptyText) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var document = factory.newDocumentBuilder().parse(output.toFile());
        var data = child(document.getDocumentElement(), version == 24 ? "datasection" : "DATASECTION");
        assertNotNull(data);
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
        assertEquals(Map.of(dataset + "-basket-1", version == 24 ? Set.of(first, second, link) : Set.of(first, second),
                dataset + "-basket-2", Set.of(third)), baskets);

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
    }

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
