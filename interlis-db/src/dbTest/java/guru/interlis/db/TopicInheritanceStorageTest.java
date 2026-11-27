package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.db.PostgisTestServer.Backend;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.mergeDeliveries;
import static guru.interlis.db.ModelFixtures.tableFor;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class TopicInheritanceStorageTest {
    private static final PostgisTestServer POSTGIS = new PostgisTestServer();

    @TempDir Path work;

    @AfterAll
    static void stopPostgis() {
        POSTGIS.close();
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
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

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
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

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
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
        fixture.assertMixedDelivery(output, "", "text", false);
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void mutatesNewClassFragmentsWithIncomingReferences(int version, Backend backend) throws Exception {
        var fixture = new TopicInheritanceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("topic-mutation.gpkg"), backend);
        String incoming = "TownIncoming" + version;
        var parentSource = fixture.source(fixture.town);
        var childSource = fixture.source(fixture.child);
        var incomingSource = fixture.source(incoming);
        assertSchemaWithIdentityColumns(db, fixture.child, childSource, "--model", incoming,
                "--model-file", parentSource.toString(), "--model-file", incomingSource.toString());
        for (var path : List.of(parentSource, childSource, incomingSource)) Files.delete(path);

        var input = fixture.input("region");
        var source = Files.readString(input);
        var independent = Files.writeString(work.resolve("independent.xtf"), source.replace("region-", "independent-"));
        var updated = fixture.updateRegion(input);
        String stationTag = Pattern.quote(version == 23 ? fixture.child + ".Region.LocalStation" : "a:LocalStation");
        String visitTag = Pattern.quote(version == 23 ? fixture.town + ".Core.Visit" : "b:Visit");
        var removed = Files.writeString(work.resolve("removed-target.xtf"),
                source.replaceFirst("(?s)<" + stationTag + " .*?</" + stationTag + ">", "")
                        .replaceFirst("(?s)<" + visitTag + ">.*?</" + visitTag + ">", "")
                        .replace(">inherited<", ">must roll back<"));
        assertCliSuccess(db, "import", fixture.child, "inherited", input);
        assertCliSuccess(db, "import", incoming, "incoming", fixture.input("incoming"));
        assertCliSuccess(db, "import", fixture.child, "independent", independent);

        String station = db.table("station");
        String destination = "SELECT destination FROM " + db.table("incominglink");
        var output = work.resolve("mutated-region.xtf");
        try (var sql = db.connect()) {
            var before = regionIds(db, sql, "inherited", "region");
            var kept = regionIds(db, sql, "independent", "independent");
            assertEquals(before.station(), scalar(sql, destination));
            assertCliSuccess(db, "update", fixture.child, "inherited", updated);
            assertEquals(before, regionIds(db, sql, "inherited", "region"));

            execute(sql, "UPDATE " + station + " SET t_type='siblingstation' WHERE t_ili_tid='region-s'");
            assertCliFailure(db, "update", fixture.child, "inherited", updated,
                    "does not belong to basket topic " + fixture.child + ".Region");
            execute(sql, "UPDATE " + station + " SET t_type='localstation' WHERE t_ili_tid='region-s'");
            assertCliFailure(db, "update", fixture.child, "inherited", removed, "referenced object");
            assertCliFailure(db, "replace", fixture.child, "inherited", input, "referenced object");
            assertCliFailure(db, "delete", fixture.child, "inherited", null, "referenced object");
            assertCliFailure(db, "delete", incoming, "inherited", null, "outside the selected model");
            assertEquals(before, regionIds(db, sql, "inherited", "region"));
            assertEquals(before.station(), scalar(sql, destination));
            assertCliSuccess(db, "export", fixture.child, "inherited", output);
            fixture.assertFragmentDelivery(output, "region", "new marker", "new base", "new child");

            assertCliSuccess(db, "delete", incoming, "incoming", null);
            assertCliSuccess(db, "replace", fixture.child, "inherited", input);
            var replaced = regionIds(db, sql, "inherited", "region");
            assertEquals(before.dataset(), replaced.dataset());
            assertNotEquals(before.station(), replaced.station());
            assertNotEquals(before.marker(), replaced.marker());
            assertNotEquals(before.basket(), replaced.basket());
            assertCliSuccess(db, "export", fixture.child, "inherited", output);
            fixture.assertFragmentDelivery(output, "region", "inherited", "base", "child");

            assertCliSuccess(db, "delete", fixture.child, "inherited", null);
            assertEquals(kept, regionIds(db, sql, "independent", "independent"));
            assertEquals("independent", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            for (var table : List.of("station", "localstation", "marker", "visit", "t_ili2db_basket")) {
                assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)), table);
            }
            assertCliSuccess(db, "export", fixture.child, "independent", output);
            fixture.assertFragmentDelivery(output, "independent", "inherited", "base", "child");
        }
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void projectsOnlyReachableBaseTopicsAndPreservesExportOnFailure(int version, Backend backend) throws Exception {
        var fixture = new TopicInheritanceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("base-export.gpkg"), backend);
        fixture.mixedSchema(db, fixture.copySources());
        assertCliSuccess(db, "import", fixture.child, "source", fixture.mixedInput(), fixture.modelsAfterChild());
        var output = work.resolve("base-export.xtf");
        assertCliSuccess(db, "export", fixture.child, null, output, "--basket", "region-b", "--export-model", fixture.town);
        fixture.assertBaseDelivery(output, "", false);
        try (var sql = db.connect()) {
            CrsFixtures.assertStoredEpsg(db, sql, "station", "center", "region-s", 2056);
        }
        var published = Files.readAllBytes(output);

        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("entry") + " SET t_seq=0 WHERE note='bravo'");
        }
        assertCliFailure(db, "export", fixture.child, null, output,
                "Duplicate or out-of-order T_Seq at " + fixture.town + ".Core.Station.History",
                "--basket", "region-b", "--export-model", fixture.town);
        assertArrayEquals(published, Files.readAllBytes(output));
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("entry") + " SET t_seq=1 WHERE note='bravo'");
        }

        assertCliFailure(db, "export", fixture.child, null, output,
                "No concrete base topic in " + fixture.town + " for " + fixture.refined + ".Region",
                "--model", fixture.refined, "--basket", "region-b", "--basket", "refine-b", "--export-model", fixture.town);
        assertArrayEquals(published, Files.readAllBytes(output));

        var links = work.resolve("base-links.xtf");
        assertCliSuccess(db, "export", fixture.links, null, links,
                "--basket", "links-b", "--export-model", fixture.linksBase);

        var original = work.resolve("source-export.xtf");
        assertCliSuccess(db, "export", fixture.child, "source", original, fixture.modelsAfterChild());
        fixture.assertMixedDelivery(original, "", "text", false);

        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("visit") + " SET visitnote=NULL");
        }
        assertCliSuccess(db, "export", fixture.child, null, output, "--basket", "region-b", "--export-model", fixture.town);
        fixture.assertBaseDelivery(output, "", false, null);
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void rejectsRolesWhenProjectionDropsTheirTarget(int version, Backend backend) throws Exception {
        var fixture = new TopicInheritanceFixtures(work, version);
        var db = POSTGIS.target(work.resolve("base-role-export.gpkg"), backend);
        var parent = fixture.source(fixture.town);
        Files.writeString(parent, Files.readString(parent)
                .replace("CLASS Marker =", "CLASS Marker (ABSTRACT) =")
                .replace("  CLASS Station =", "  CLASS DefaultMarker EXTENDS Marker = END DefaultMarker;\n  CLASS Station ="));
        var child = fixture.source(fixture.child);
        String localMarker = "CLASS LocalMarker EXTENDS " + fixture.town + ".Core.Marker = END LocalMarker;";
        Files.writeString(child, Files.readString(child).replace("    CLASS LocalStation",
                "    " + localMarker + "\n    CLASS LocalStation"));
        assertSchemaWithIdentityColumns(db, fixture.child, child, "--model-file", parent.toString());

        var input = fixture.input("region");
        String baseTag = version == 23 ? fixture.town + ".Core.Marker" : "b:Marker";
        String localTag = version == 23 ? fixture.child + ".Region.LocalMarker" : "a:LocalMarker";
        Files.writeString(input, Files.readString(input).replace(baseTag, localTag));
        assertCliSuccess(db, "import", fixture.child, "source", input);
        try (var sql = db.connect()) {
            execute(sql, "DELETE FROM " + db.table("visit"));
        }

        var output = work.resolve("base-role-export.xtf");
        assertCliSuccess(db, "export", fixture.child, "source", output);
        var published = Files.readAllBytes(output);
        assertCliFailure(db, "export", fixture.child, null, output,
                "Missing projected target region-m at " + fixture.town + ".Core.Location.LocatedAt of object region-s in basket region-b",
                "--basket", "region-b", "--export-model", fixture.town);
        assertArrayEquals(published, Files.readAllBytes(output));

        assertCliSuccess(db, "replace", fixture.child, "source", input);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("station") + " SET locatedat=NULL");
        }
        assertCliFailure(db, "export", fixture.child, null, output,
                "Missing projected target region-m at " + fixture.town + ".Core.Visit.VisitedMarker of link "
                        + fixture.town + ".Core.Visit in basket region-b",
                "--basket", "region-b", "--export-model", fixture.town);
        assertArrayEquals(published, Files.readAllBytes(output));
    }

    @ParameterizedTest(name = "{displayName} [XTF {0}, {1}]")
    @CsvSource({"23, GEOPACKAGE", "24, GEOPACKAGE", "23, POSTGIS", "24, POSTGIS"})
    void requiresReachableExportModelsAndPreservesPublishedFile(int version, Backend backend) throws Exception {
        var fixture = new ExportModelsFixtures(work, version);
        var db = POSTGIS.target(work.resolve("export-models.gpkg"), backend);
        fixture.createSchema(db, List.of(fixture.child, fixture.otherChild));
        assertCliSuccess(db, "import", fixture.child, "regions", fixture.delivery, "--model", fixture.otherChild);
        var output = work.resolve("exported.xtf");
        assertCliSuccess(db, "export", fixture.child, "regions", output, fixture.exportOptions(fixture.targets()));
        fixture.assertDelivery(output, fixture.targets());
        var published = Files.readAllBytes(output);

        assertCliFailure(db, "export", fixture.child, "regions", output,
                "for " + fixture.otherChild + ".South", fixture.exportOptions(List.of(fixture.parent, fixture.grand)));
        assertArrayEquals(published, Files.readAllBytes(output));
        assertCliFailure(db, "export", fixture.child, "regions", output,
                "UnknownTarget", fixture.exportOptions(List.of(fixture.parent, "UnknownTarget")));
        assertArrayEquals(published, Files.readAllBytes(output));
    }

    private record RegionIds(String station, String marker, String basket, String dataset) { }

    private static RegionIds regionIds(Target db, Connection sql, String dataset, String prefix) throws Exception {
        String baskets = db.table("t_ili2db_basket");
        String datasetId = scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='" + dataset + "'");
        String basket = scalar(sql, "SELECT t_id FROM " + baskets + " WHERE t_ili_tid='" + prefix + "-b' AND dataset=" + datasetId);
        String station = scalar(sql, "SELECT t_id FROM " + db.table("station")
                + " WHERE t_ili_tid='" + prefix + "-s' AND t_type='localstation' AND t_basket=" + basket);
        String marker = scalar(sql, "SELECT t_id FROM " + db.table("marker") + " WHERE t_ili_tid='" + prefix + "-m' AND t_basket=" + basket);
        assertNotNull(datasetId);
        assertNotNull(basket);
        assertNotNull(station);
        assertNotNull(marker);
        assertEquals(station, scalar(sql, "SELECT t_id FROM " + db.table("localstation") + " WHERE t_id=" + station + " AND t_basket=" + basket));
        return new RegionIds(station, marker, basket, datasetId);
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
