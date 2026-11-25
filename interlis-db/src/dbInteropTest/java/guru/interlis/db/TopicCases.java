package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class TopicCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;
    private final TopicInheritanceFixtures fixtures;

    TopicCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
        fixtures = new TopicInheritanceFixtures(work, version);
    }

    void mutation(TestDatabase db) throws Exception {
        String parent = "TownParent" + version;
        String model = "TownChild" + version;
        String incoming = "TownIncoming" + version;
        var baseSource = fixture(parent + ".ili");
        var derivedSource = fixture(model + ".ili");
        var incomingSource = fixture(incoming + ".ili");
        referenceSchema(db, model + ";" + incoming);
        for (var file : List.of(baseSource, derivedSource, incomingSource)) Files.delete(file);
        var input = fixture("region" + version + ".xtf");
        String source = Files.readString(input);
        referenceImport(db, "inherited", input);
        referenceImport(db, "incoming", fixture("incoming" + version + ".xtf"));
        var independent = Files.writeString(work.resolve("region-independent.xtf"), source.replace("region-", "independent-"));
        referenceImport(db, "independent", independent);
        var updated = Files.writeString(work.resolve("region-updated.xtf"),
                source.replace(">inherited<", ">new marker<").replace(">base<", ">new base<").replace(">child<", ">new child<"));
        var removed = Files.writeString(work.resolve("region-removes-target.xtf"), updatedTextWithoutStation(source));
        var output = work.resolve("region-mutation.xtf");
        String station = db.table("station");
        String ownStation = "SELECT t_id FROM " + station + " WHERE t_ili_tid='region-s'";
        String fragment = "SELECT l.t_id FROM " + db.table("localstation") + " l JOIN " + station
                + " s ON s.t_id=l.t_id AND s.t_basket=l.t_basket WHERE s.t_ili_tid='region-s'";
        String marker = "SELECT t_id FROM " + db.table("marker") + " WHERE t_ili_tid='region-m'";
        String basket = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='region-b'";
        String dataset = "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='inherited'";
        try (var sql = db.connect()) {
            String oldStation = scalar(sql, ownStation);
            String oldMarker = scalar(sql, marker);
            String oldBasket = scalar(sql, basket);
            String oldDataset = scalar(sql, dataset);
            assertEquals(oldStation, scalar(sql, "SELECT destination FROM " + db.table("incominglink")));
            run(db, "update", model, "inherited", updated);
            assertEquals(oldStation, scalar(sql, ownStation));
            assertEquals(oldStation, scalar(sql, fragment));
            assertEquals(oldMarker, scalar(sql, marker));
            assertEquals(oldBasket, scalar(sql, basket));
            referenceExport(db, "inherited", output);
            fixtures.assertFragmentDelivery(output, "region", "new marker", "new base", "new child");

            execute(sql, "UPDATE " + station + " SET t_type='siblingstation' WHERE t_ili_tid='region-s'");
            assertCliFailure(db, "update", model, "inherited", updated, "does not belong to basket topic " + model + ".Region");
            execute(sql, "UPDATE " + station + " SET t_type='localstation' WHERE t_ili_tid='region-s'");
            assertCliFailure(db, "update", model, "inherited", removed, "referenced object");
            assertCliFailure(db, "replace", model, "inherited", input, "referenced object");
            assertCliFailure(db, "delete", model, "inherited", null, "referenced object");
            assertCliFailure(db, "delete", incoming, "inherited", null, "outside the selected model");
            assertEquals(oldStation, scalar(sql, fragment));
            assertEquals(oldMarker, scalar(sql, marker));
            assertEquals(oldBasket, scalar(sql, basket));
            run(db, "export", model, "inherited", output);
            fixtures.assertFragmentDelivery(output, "region", "new marker", "new base", "new child");

            run(db, "delete", incoming, "incoming", null);
            run(db, "replace", model, "inherited", input);
            assertEquals(oldDataset, scalar(sql, dataset));
            assertNotEquals(oldStation, scalar(sql, ownStation));
            assertEquals(scalar(sql, ownStation), scalar(sql, fragment));
            referenceExport(db, "inherited", output);
            fixtures.assertFragmentDelivery(output, "region", "inherited", "base", "child");
            run(db, "delete", model, "inherited", null);
            assertEquals("independent", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            for (var table : List.of("station", "localstation", "marker", "t_ili2db_basket")) {
                assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)), table);
            }
            referenceExport(db, "independent", output);
            fixtures.assertFragmentDelivery(output, "independent", "inherited", "base", "child");
        }
    }

    private String updatedTextWithoutStation(String source) {
        return source.lines().filter(line -> !line.contains("region-s")).collect(java.util.stream.Collectors.joining("\n"))
                .replace(">inherited<", ">must roll back<");
    }

    private void referenceSchema(TestDatabase db, String models) throws Exception {
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models);
    }

    private Path fixture(String name) throws Exception {
        var path = work.resolve(name);
        try (var resource = getClass().getResourceAsStream("/topics/" + name)) {
            Files.copy(resource, path);
        }
        return path;
    }

    private void referenceImport(TestDatabase db, String dataset, Path input) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--import", "--dataset", dataset, "--importBid", "--importTid", input.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path output) throws Exception {
        reference.run(db, "%ILI_FROM_DB", "--export", "--dataset", dataset, "--exportTid", output.toString());
    }

    private void run(TestDatabase db, String operation, String model, String dataset, Path path, String... options) {
        var result = cli(db, operation, model, dataset, path, CancellationToken.none(), options);
        assertEquals(0, result.code(), result::diagnostics);
    }
}
