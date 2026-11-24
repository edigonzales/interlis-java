package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class SmartMutationStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void stableIdsAndIncomingReferences(Backend backend, int version) throws Exception {
        var fixtures = new SmartFixtures(work, version);
        String parent = "TownParent" + version;
        String model = "TownChild" + version;
        String incoming = "TownIncoming" + version;
        Path parentIli = fixtures.fixture("/topics/", parent + ".ili");
        Path childIli = fixtures.fixture("/topics/", model + ".ili");
        Path incomingIli = fixtures.fixture("/topics/", incoming + ".ili");
        Path input = fixtures.fixture("/topics/", "region" + version + ".xtf");
        Path referenceInput = fixtures.fixture("/topics/", "incoming" + version + ".xtf");
        String original = Files.readString(input);
        Path changed = fixtures.changedRegion(input);
        Path withoutTarget = Files.writeString(work.resolve("region-removed.xtf"),
                original.lines().filter(line -> !line.contains("region-s")).collect(Collectors.joining("\n"))
                        .replace(">inherited<", ">must roll back<"));
        String changedType = version == 23
                ? original.replace(model + ".Region.LocalStation", parent + ".Core.Station").replace("<LocalMemo>child</LocalMemo>", "")
                : original.replace("a:LocalStation", "b:Station").replace("<a:LocalMemo>child</a:LocalMemo>", "");
        Path wrongType = Files.writeString(work.resolve("region-type-change.xtf"), changedType);

        for (String layout : List.of("smart1", "smart2")) {
            var db = POSTGIS.target(work.resolve(layout + ".gpkg"), backend);
            assertSchemaWithIdentityColumns(db, model, childIli, "--model", incoming, "--model-file", parentIli.toString(),
                    "--model-file", incomingIli.toString(), "--inheritance", layout);
            assertCliSuccess(db, "import", model, "region", input);
            assertCliSuccess(db, "import", incoming, "incoming", referenceInput);
            String station = db.table(layout.equals("smart1") ? "station" : "localstation");
            String row = " FROM " + station + " WHERE t_ili_tid='region-s'";
            String basket = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='region-b'";
            String dataset = "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='region'";
            try (var sql = db.connect()) {
                String previous = scalar(sql, "SELECT t_id" + row);
                String previousBasket = scalar(sql, basket);
                String previousDataset = scalar(sql, dataset);
                assertNotNull(previous);
                assertEquals(previous, scalar(sql, "SELECT destination FROM " + db.table("incominglink")));
                assertCliSuccess(db, "update", model, "region", changed);
                assertEquals(previous, scalar(sql, "SELECT t_id" + row));
                assertEquals(previousBasket, scalar(sql, basket));
                assertEquals("updated", scalar(sql, "SELECT localmemo" + row));

                assertCliFailure(db, "update", model, "region", wrongType, "Cannot change concrete class");
                assertCliFailure(db, "update", model, "region", withoutTarget, "referenced object");
                assertCliFailure(db, "replace", model, "region", input, "referenced object");
                assertCliFailure(db, "delete", model, "region", null, "referenced object");
                assertEquals(previous, scalar(sql, "SELECT t_id" + row));
                assertEquals(previousBasket, scalar(sql, basket));
                assertEquals(previousDataset, scalar(sql, dataset));
                assertEquals(previous, scalar(sql, "SELECT destination FROM " + db.table("incominglink")));
                assertEquals("marker updated", scalar(sql, "SELECT memo FROM " + db.table("marker") + " WHERE t_ili_tid='region-m'"));
                Path output = work.resolve(layout + "-mutated.xtf");
                assertCliSuccess(db, "export", model, "region", output);
                fixtures.assertRegion(output, "updated");

                assertCliSuccess(db, "delete", incoming, "incoming", null);
                assertCliSuccess(db, "replace", model, "region", input);
                assertEquals(previousDataset, scalar(sql, dataset));
                assertNotEquals(previous, scalar(sql, "SELECT t_id" + row));
                assertCliSuccess(db, "export", model, "region", output);
                fixtures.assertRegion(output, "child");
                assertCliSuccess(db, "delete", model, "region", null);
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + station));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("marker")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            }
        }
    }

    @ParameterizedTest(name = "{0}, XTF {1}")
    @CsvSource({"GEOPACKAGE,23", "GEOPACKAGE,24", "POSTGIS,23", "POSTGIS,24"})
    void removesOptionalAndSecondaryGeometries(Backend backend, int version) throws Exception {
        var fixtures = new SmartFixtures(work, version);
        String model = "SmartGeometry" + version;
        Path modelFile = fixtures.fixture(model + ".ili");
        Path input = fixtures.fixture(model + ".xtf");
        Path changed = Files.writeString(work.resolve("geometry-remove-optional.xtf"),
                Files.readString(input).lines().filter(line -> !line.contains("fixture-n")).collect(Collectors.joining("\n"))
                        .replaceAll("(?s)<ExtraLocation>.*?</ExtraLocation>", ""));
        for (String layout : List.of("smart1", "smart2")) {
            var db = POSTGIS.target(work.resolve(layout + ".gpkg"), backend);
            assertSchemaWithIdentityColumns(db, model, modelFile, "--inheritance", layout);
            assertCliSuccess(db, "import", model, "geometry", input);
            assertCliSuccess(db, "update", model, "geometry", changed);
            Path output = work.resolve(layout + "-geometry.xtf");
            assertCliSuccess(db, "export", model, "geometry", output);
            var basket = children(readDataSection(output, version)).getFirst();
            assertEquals(3, children(basket).size());
            var smart = fixtures.object(basket, model, "SmartUnit", "fixture-s");
            assertNotNull(child(smart, "Location"));
            assertNull(child(smart, "ExtraLocation"));

            assertCliSuccess(db, "replace", model, "geometry", input);
            assertCliSuccess(db, "export", model, "geometry", output);
            fixtures.assertGeometries(output, model, "fixture");
            assertCliSuccess(db, "delete", model, "geometry", null);
            try (var sql = db.connect()) {
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
                String parent = layout.equals("smart1") ? "meter" : "smartunit";
                assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(parent)));
                if (!db.postgis()) {
                    String helper = layout.equals("smart1") ? "meter_extralocation" : "smartunit_location";
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(helper)));
                }
            }
        }
    }
}
