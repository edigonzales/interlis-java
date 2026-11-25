package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.mergeDeliveries;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TopicInheritanceStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsSiblingClassWithoutReplacingExport(int version, Backend backend) throws Exception {
        var fixture = new TopicInheritanceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("sibling-class.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.child, fixture.source(fixture.child),
                "--model-file", fixture.source(fixture.town).toString());
        assertCliSuccess(db, "import", fixture.child, "region", fixture.input("region"));
        var output = work.resolve("region-export.xtf");
        assertCliSuccess(db, "export", fixture.child, "region", output);
        fixture.assertFragmentDelivery(output, "region", "inherited", "base", "child");
        var published = Files.readAllBytes(output);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("station") + " SET t_type='siblingstation' WHERE t_ili_tid='region-s'");
        }
        assertCliFailure(db, "export", fixture.child, "region", output, "does not belong to basket topic " + fixture.child + ".Region");
        assertArrayEquals(published, Files.readAllBytes(output));
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsAbstractBasketWithoutReplacingExport(int version, Backend backend) throws Exception {
        var fixture = new TopicInheritanceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("abstract-basket.gpkg"), backend);
        assertSchemaWithIdentityColumns(db, fixture.refined, fixture.source(fixture.refined),
                "--model-file", fixture.source(fixture.abstractTown).toString());
        assertCliSuccess(db, "import", fixture.refined, "refined", fixture.input("refine"));
        var output = work.resolve("refined-export.xtf");
        assertCliSuccess(db, "export", fixture.refined, "refined", output);
        fixture.assertRefinementDelivery(output, "refine", "text");
        var published = Files.readAllBytes(output);
        try (var sql = db.connect()) {
            String baskets = db.table("t_ili2db_basket");
            execute(sql, "INSERT INTO " + baskets + " (t_id, topic, t_ili_tid, attachmentkey, dataset)"
                    + " SELECT 100000, '" + fixture.abstractTown + ".Core', 'abstract-b', 'abstract-b', dataset FROM " + baskets
                    + " WHERE t_ili_tid='refine-b'");
        }
        assertCliFailure(db, "export", fixture.refined, "refined", output,
                "Basket uses an abstract topic: " + fixture.abstractTown + ".Core", "--model", fixture.abstractTown);
        assertArrayEquals(published, Files.readAllBytes(output));
    }

    @ParameterizedTest(name = "XTF {0}, {1}")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rollsBackWhenInheritedReferenceTargetsWrongBasket(int version, Backend backend) throws Exception {
        var fixture = new TopicInheritanceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("wrong-basket.gpkg"), backend);
        fixture.mixedSchema(db, fixture.copySources());
        var valid = fixture.mixedInput();
        assertCliSuccess(db, "import", fixture.child, "kept", valid, fixture.modelsAfterChild());
        var markerTable = tableFor(db, fixture.town + ".Core.Marker");
        var before = rowCounts(db, markerTable);
        var flat = Files.writeString(work.resolve("wrong-flat.xtf"), Files.readString(work.resolve("flat" + version + ".xtf"))
                .replace("flat-", "bad-flat-"));
        var links = wrongBasketDelivery(version);
        var bad = mergeDeliveries(work.resolve("wrong-basket.xtf"), version, flat, links);
        assertCliFailure(db, "import", fixture.child, "wrong-basket", bad,
                "to bad-depot as an ID in the mapped target table in this basket", fixture.modelsAfterChild());
        assertEquals(before, rowCounts(db, markerTable));

        var output = work.resolve("kept-export.xtf");
        assertCliSuccess(db, "export", fixture.child, "kept", output, fixture.modelsAfterChild());
        fixture.assertMixedDelivery(output, "", "text");
    }

    private Path wrongBasketDelivery(int version) throws Exception {
        String depot = version == 23 ? "<LinksBase23.Core.Depot TID=\"bad-depot\"/>" : "<p:Depot i:tid=\"bad-depot\"/>";
        String region = version == 23 ? "</LinksTown23.Region>" : "</d:Region>";
        String sibling = version == 23 ? "<LinksTown23.Sibling BID=\"bad-sibling\">" + depot + "</LinksTown23.Sibling>"
                : "<d:Sibling i:bid=\"bad-sibling\">" + depot + "</d:Sibling>";
        var source = Files.readString(work.resolve("links" + version + ".xtf"));
        return Files.writeString(work.resolve("wrong-links.xtf"), source.replace("links-", "bad-")
                .replace(depot, "").replace(region, region + "\n" + sibling));
    }

    private static Map<String, String> rowCounts(Target db, String markerTable) throws Exception {
        var counts = new LinkedHashMap<String, String>();
        try (var sql = db.connect()) {
            var tables = List.of("t_ili2db_dataset", "t_ili2db_basket", markerTable, "desk", "delivery", "note", "depot", "crate", "registerentry");
            for (var table : tables) counts.put(table, scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)));
        }
        return counts;
    }
}
