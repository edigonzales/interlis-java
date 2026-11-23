package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static guru.interlis.db.DbInteropTest.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.referenceDatasetFixture;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatasetWriteCases {
    private final Path work;
    private final int version;
    private final ReferenceTool reference;

    DatasetWriteCases(Path work, int version, ReferenceTool reference) {
        this.work = work;
        this.version = version;
        this.reference = reference;
    }

    void deleteRelations(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbInterop" + version;
        schema(db, referenceSchema, model);
        Path input = work.resolve("delete-source.xtf");
        if (Files.notExists(input)) {
            try (var resource = DatasetWriteCases.class.getResourceAsStream("/" + model + ".xtf")) {
                Files.copy(resource, input);
            }
        }
        seed(db, referenceSchema, model, "remove", input);
        seed(db, referenceSchema, model, "retain", input);
        try (var sql = db.connect()) {
            String datasets = db.table("t_ili2db_dataset");
            String baskets = db.table("t_ili2db_basket");
            String removed = scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='remove'");
            String retained = scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='retain'");
            var removedBaskets = strings(sql, "SELECT t_id FROM " + baskets + " WHERE dataset=" + removed);
            var retainedBaskets = strings(sql, "SELECT t_id FROM " + baskets + " WHERE dataset=" + retained);
            var tables = List.of("mast", "tower", "place", "nestedleaf", "nestedinner", "markerlink");
            var removedRows = basketObjectIds(db, sql, tables, removedBaskets);
            var retainedRows = basketObjectIds(db, sql, tables, retainedBaskets);
            assertTrue(removedRows.values().stream().allMatch(ids -> !ids.isEmpty()));

            String pin = db.table("prevent_dataset_delete");
            execute(sql, "CREATE TABLE " + pin + " (dataset BIGINT REFERENCES " + datasets + "(t_id))");
            execute(sql, "INSERT INTO " + pin + " VALUES (" + removed + ")");
            var failed = cli(db, "delete", model, "remove", null, CancellationToken.none());
            assertNotEquals(0, failed.code(), failed::diagnostics);
            assertEquals(removed, scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='remove'"));
            assertEquals(removedRows, basketObjectIds(db, sql, tables, removedBaskets));
            execute(sql, "DROP TABLE " + pin);

            if (referenceSchema) assertCliSuccess(db, "delete", model, "remove", null);
            else reference.run(db, work.toString(), "--delete", "--dataset", "remove");

            assertNull(scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='remove'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + baskets + " WHERE dataset=" + removed));
            assertTrue(basketObjectIds(db, sql, tables, removedBaskets).values().stream().allMatch(List::isEmpty));
            assertEquals(retainedRows, basketObjectIds(db, sql, tables, retainedBaskets));
            failed = cli(db, "delete", model, "remove", null, CancellationToken.none());
            assertNotEquals(0, failed.code(), failed::diagnostics);
            assertTrue(failed.diagnostics().contains("Dataset does not exist"), failed::diagnostics);

            long largest = Long.parseLong(scalar(sql, "SELECT max(t_id) FROM " + baskets));
            runReference(db, "import", "tail", input);
            assertTrue(Long.parseLong(scalar(sql, "SELECT t_id FROM " + datasets + " WHERE datasetname='tail'")) > largest);
        }
    }

    private static Map<String, List<String>> basketObjectIds(TestDatabase db, Connection sql, List<String> tables,
                                                              List<String> baskets) throws SQLException {
        var result = new HashMap<String, List<String>>();
        for (String table : tables) {
            result.put(table, strings(sql, "SELECT t_id FROM " + db.table(table) + " WHERE t_basket IN ("
                    + String.join(", ", baskets) + ") ORDER BY t_id"));
        }
        return result;
    }

    void deleteReferenced(TestDatabase db) throws Exception {
        String model = "DbMutRef" + version;
        schema(db, true, model);
        Path catalog = delivery("delete-catalog", model, new Basket("catalog", target(model, "site", "listed")));
        Path source = delivery("delete-dependent", model,
                new Basket("source", recordWithReference(model, "record", "observed", "site", "catalog")));
        runReference(db, "import", "catalog", catalog);
        assertCliSuccess(db, "import", model, "source", source);
        try (var sql = db.connect()) {
            var targets = rows(db, sql, "mutationtarget");
            var result = cli(db, "delete", model, "catalog", null, CancellationToken.none());
            assertNotEquals(0, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("Cannot delete referenced object"), result::diagnostics);
            assertEquals(targets, rows(db, sql, "mutationtarget"));
            assertDestination(db, sql, rows(db, sql, "mutationrecord").get(key("source", "source", "record")),
                    targets.get(key("catalog", "catalog", "site")));
            assertCliSuccess(db, "delete", model, "source", null);
            assertCliSuccess(db, "delete", model, "catalog", null);
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
        }
    }

    void replace(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutation" + version;
        schema(db, referenceSchema, model);
        Path first = delivery("original", model, new Basket("main", item(model, "kept", "old")),
                new Basket("obsolete", item(model, "removed", "gone")));
        Path other = delivery("isolated", model, new Basket("other", item(model, "kept", "other dataset")));
        Path replacement = delivery("replacement", model, new Basket("main", item(model, "kept", "changed")));
        seed(db, referenceSchema, model, "main", first);
        seed(db, referenceSchema, model, "other", other);

        try (var sql = db.connect()) {
            var old = rows(db, sql, "mutationitem");
            assertEquals(3, old.size());
            checkConcurrentWriter(db, model, replacement);
            assertEquals(old, rows(db, sql, "mutationitem"));

            Path invalid = Files.writeString(work.resolve("late-error.xtf"), Files.readString(replacement) + "<trailing/>");
            var failure = cli(db, "replace", model, "main", invalid, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertEquals(old, rows(db, sql, "mutationitem"));

            Path exported = mutate(db, referenceSchema, "replace", model, "main", replacement);
            var actual = rows(db, sql, "mutationitem");
            assertEquals(2, actual.size());
            var previous = old.get(key("main", "main", "kept"));
            var current = actual.get(key("main", "main", "kept"));
            assertEquals(previous.dataset(), current.dataset());
            assertNotEquals(previous.basket(), current.basket());
            assertNotEquals(previous.object(), current.object());
            assertEquals("changed", current.label());
            assertEquals(old.get(key("other", "other", "kept")), actual.get(key("other", "other", "kept")));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")
                    + " WHERE t_ili_tid = 'obsolete'"));
            assertSingleExport(exported, "main", "kept", "changed");

            Path created = delivery("created", model, new Basket("created", item(model, "new", "new dataset")));
            assertCliSuccess(db, "replace", model, "created", created);
            assertEquals("new dataset", rows(db, sql, "mutationitem").get(key("created", "created", "new")).label());
            runReference(db, "import", "tail", created);
            var tail = rows(db, sql, "mutationitem").get(key("tail", "created", "new"));
            assertTrue(tail.object() > current.object());
        }
    }

    void replaceRelations(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbInterop" + version;
        schema(db, referenceSchema, model);
        Path original = work.resolve("replace-original.xtf");
        if (Files.notExists(original)) {
            try (var resource = DatasetWriteCases.class.getResourceAsStream("/" + model + ".xtf")) {
                Files.copy(resource, original);
            }
        }
        seed(db, referenceSchema, model, "rel", original);
        String fields = "<Links>" + structure(model, "LinkDetail",
                "<Label>in new basket</Label>" + reference("Destination", "site", "")) + "</Links>";
        String geometry = version == 23 ? "<COORD><C1>2600600.500</C1><C2>1200600.250</C2></COORD>"
                : "<g:coord><g:c1>2600600.500</g:c1><g:c2>1200600.250</g:c2></g:coord>";
        String objects = object(model, "Report", "report", fields)
                + object(model, "Place", "site", "<Geometry>" + geometry + "</Geometry>");
        Path replacement = delivery("replace-rel", model, new Basket("new-basket", objects));

        try (var sql = db.connect()) {
            var originalBaskets = new ArrayList<Long>();
            try (var statement = sql.createStatement();
                 var rows = statement.executeQuery("SELECT t_id FROM " + db.table("t_ili2db_basket"))) {
                while (rows.next()) originalBaskets.add(rows.getLong(1));
            }
            assertFalse(originalBaskets.isEmpty());
            Path exported = mutate(db, referenceSchema, "replace", model, "rel", replacement);
            for (String table : List.of("mast", "tower", "nestedleaf", "nestedinner", "markerlink", "place", "linkdetail")) {
                for (long basket : originalBaskets) {
                    assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table(table) + " WHERE t_basket = " + basket), table);
                }
            }
            assertEquals("site", scalar(sql, "SELECT p.t_ili_tid FROM " + db.table("place") + " p JOIN "
                    + db.table("linkdetail") + " d ON d.destination=p.t_id"));
            String srid = db.postgis() ? "SELECT ST_SRID(ageometry) FROM " + db.table("place")
                    : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name = 'place'";
            assertEquals("2056", scalar(sql, srid));
            var baskets = children(readDataSection(exported, version));
            assertEquals(1, baskets.size());
            assertEquals("new-basket", id(baskets.getFirst(), version, "bid"));
            var place = child(baskets.getFirst(), version == 23 ? model + ".Data.Place" : "Place");
            assertEquals("site", id(place, version, "tid"));
            String x = children(children(child(place, "Geometry")).getFirst()).getFirst().getTextContent();
            assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal("2600600.500")));
        }
    }

    void replaceExternal(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutRef" + version;
        schema(db, referenceSchema, model);
        Path catalog = delivery("replace-catalog", model,
                new Basket("catalog", object(model, "MutationTarget", "destination", "<Label>listed</Label>")));
        Path source = delivery("replace-ext-source", model,
                new Basket("source", recordWithReference(model, "record", "original", "destination", "catalog")));
        Path replacement = delivery("replace-ext-new", model,
                new Basket("source", recordWithReference(model, "record", "replacement", "destination", "catalog")));
        seed(db, referenceSchema, model, "catalog", catalog);
        seed(db, referenceSchema, model, "source", source);

        try (var sql = db.connect()) {
            var target = rows(db, sql, "mutationtarget").get(key("catalog", "catalog", "destination"));
            var failed = cli(db, "replace", model, "catalog", catalog, CancellationToken.none());
            assertNotEquals(0, failed.code(), failed::diagnostics);
            assertTrue(failed.diagnostics().contains("referenced object T_Id " + target.object()), failed::diagnostics);
            assertEquals(target, rows(db, sql, "mutationtarget").get(key("catalog", "catalog", "destination")));
            String readLink = "SELECT destination FROM " + db.table("mutationlink");
            assertEquals(Long.toString(target.object()), scalar(sql, readLink));

            Path output = mutate(db, referenceSchema, "replace", model, "source", replacement);
            assertSingleExport(output, "source", "record", "replacement");
            assertEquals(Long.toString(target.object()), scalar(sql, readLink));
            assertEquals(target, rows(db, sql, "mutationtarget").get(key("catalog", "catalog", "destination")));
        }
    }

    private String recordWithReference(String model, String tid, String label, String target, String bid) {
        return object(model, "MutationRecord", tid, "<Label>" + label + "</Label><Detail>"
                + structure(model, "MutationLink", reference("Destination", target, bid)) + "</Detail>");
    }

    void updateFlat(TestDatabase db, boolean referenceSchema, boolean stable) throws Exception {
        String model = (stable ? "DbMutStable" : "DbMutation") + version;
        schema(db, referenceSchema, model);
        String main = identity("main", stable);
        String later = identity("later", stable);
        String clear = identity("clear", stable);
        String untouched = identity("untouched", stable);
        String foreign = identity("foreign", stable);
        String kept = identity("kept", stable);
        String added = identity("added", stable);
        String standard = stable ? object(model, "MutationStandard", "ch20340000000001", "<Label>before</Label>") : "";
        String newStandard = stable ? standard.replace("before", "after") : "";
        Path original = delivery("update-original", model, new Basket(main,
                        item(model, kept, "before") + item(model, identity("removed", stable), "removed") + standard),
                new Basket(clear, item(model, identity("to-clear", stable), "clear me")),
                new Basket(untouched, item(model, identity("untouched-item", stable), "leave")));
        Path other = delivery("update-foreign", model,
                new Basket(foreign, item(model, identity("their-item", stable), "other dataset")));
        Path changed = delivery("updated", model, new Basket(main,
                        item(model, kept, "after") + item(model, added, "added") + newStandard),
                new Basket(clear, ""), new Basket(later, item(model, identity("another", stable), "new basket")));
        seed(db, referenceSchema, model, "main", original);
        seed(db, referenceSchema, model, "other", other);

        try (var sql = db.connect()) {
            var before = rows(db, sql, "mutationitem");
            var keeper = key("main", main, kept);
            long emptyBasket = before.get(key("main", clear, identity("to-clear", stable))).basket();
            Row oldStandard = stable ? rows(db, sql, "mutationstandard").get(key("main", main, "ch20340000000001")) : null;

            var rejected = cli(db, "update", model, "main", other, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertTrue(rejected.diagnostics().contains("another dataset or topic"), rejected::diagnostics);
            Path invalid = Files.writeString(work.resolve("late-update-error.xtf"), Files.readString(changed) + "<trailing/>");
            rejected = cli(db, "update", model, "main", invalid, CancellationToken.none());
            assertNotEquals(0, rejected.code(), rejected::diagnostics);
            assertEquals(before, rows(db, sql, "mutationitem"));

            Path output = mutate(db, referenceSchema, "update", model, "main", changed);
            var after = rows(db, sql, "mutationitem");
            assertEquals(5, after.size(), after.toString());
            assertEquals(before.get(keeper).withLabel("after"), after.get(keeper));
            assertEquals(before.get(key("main", untouched, identity("untouched-item", stable))),
                    after.get(key("main", untouched, identity("untouched-item", stable))));
            assertEquals(before.get(key("other", foreign, identity("their-item", stable))),
                    after.get(key("other", foreign, identity("their-item", stable))));
            assertEquals("added", after.get(key("main", main, added)).label());
            assertEquals("new basket", after.get(key("main", later, identity("another", stable))).label());
            assertEquals(Long.toString(emptyBasket), scalar(sql, "SELECT t_id FROM " + db.table("t_ili2db_basket")
                    + " WHERE t_ili_tid='" + clear + "'"));
            assertEquals("0", scalar(sql, "SELECT count(*) FROM " + db.table("mutationitem") + " WHERE t_basket=" + emptyBasket));
            if (stable) assertEquals(oldStandard.withLabel("after"),
                    rows(db, sql, "mutationstandard").get(key("main", main, "ch20340000000001")));
            var exported = children(readDataSection(output, version)).stream().flatMap(b -> children(b).stream())
                    .filter(o -> kept.equals(id(o, version, "tid"))).findFirst().orElseThrow();
            assertEquals("after", child(exported, "Label").getTextContent());

            Path fresh = delivery("created-by-update", model, new Basket(identity("fresh", stable),
                    item(model, identity("fresh-item", stable), "first version")));
            assertCliSuccess(db, "update", model, "fresh", fresh);
            assertEquals("first version", rows(db, sql, "mutationitem")
                    .get(key("fresh", identity("fresh", stable), identity("fresh-item", stable))).label());
            if (!stable) checkStoredIdentities(db, sql, model, changed, after);
        }
    }

    void updateGeometry(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutGeom" + version;
        schema(db, referenceSchema, model);
        Path original = delivery("geom-before", model, new Basket("geometries",
                object(model, "GeoLeaf", "kept", "<Label>old</Label>" + point("2600000.125") + "<DetailCode>old</DetailCode>")
                + object(model, "GeoLeaf", "clear", "<Label>clear</Label>" + point("2600050.500"))
                + object(model, "GeoLeaf", "removed", "<Label>removed</Label>")));
        Path changed = delivery("geom-after", model, new Basket("geometries",
                object(model, "GeoLeaf", "kept", "<Label>now</Label>" + point("2600600.500") + "<DetailCode>new</DetailCode>")
                + object(model, "GeoLeaf", "clear", "<Label>empty</Label>")));
        seed(db, referenceSchema, model, "geom", original);
        try (var sql = db.connect()) {
            var before = rows(db, sql, "georoot");
            Path switched = delivery("geom-wrong-type", model, new Basket("geometries",
                    object(model, "GeoSibling", "kept", "<Label>other class</Label>")));
            var failure = cli(db, "update", model, "geom", switched, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("Cannot change concrete class"), failure::diagnostics);
            assertEquals(before, rows(db, sql, "georoot"));

            Path output = mutate(db, referenceSchema, "update", model, "geom", changed);
            var current = rows(db, sql, "georoot");
            var kept = key("geom", "geometries", "kept");
            assertEquals(before.get(kept).withLabel("now"), current.get(kept));
            assertEquals(before.get(key("geom", "geometries", "clear")).withLabel("empty"),
                    current.get(key("geom", "geometries", "clear")));
            assertEquals(2, current.size());
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("geoleaf")));
            assertEquals("new", scalar(sql, "SELECT detailcode FROM " + db.table("geoleaf")
                    + " WHERE t_id=" + current.get(kept).object() + " AND t_basket=" + current.get(kept).basket()));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("georoot")
                    + " WHERE t_ili_tid='clear' AND center IS NULL"));
            var pointOwner = children(children(readDataSection(output, version)).getFirst()).stream()
                    .filter(o -> "kept".equals(id(o, version, "tid"))).findFirst().orElseThrow();
            String x = children(children(child(pointOwner, "Center")).getFirst()).getFirst().getTextContent();
            assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal("2600600.500")));
        }
    }

    private String point(String x) {
        return version == 23 ? "<Center><COORD><C1>%s</C1><C2>1200000.375</C2></COORD></Center>".formatted(x)
                : "<Center><g:coord><g:c1>%s</g:c1><g:c2>1200000.375</g:c2></g:coord></Center>".formatted(x);
    }

    void updateStructures(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutStruct" + version;
        schema(db, referenceSchema, model);
        String oldTree = tree(model, "old one", List.of("old entry", "discard"), List.of("old flag", "other"));
        Path initial = delivery("tree-before", model, new Basket("trees",
                object(model, "TreeRecord", "keeper", "<Label>before</Label>" + oldTree)
                + object(model, "TreeRecord", "clear", "<Label>clear</Label>" + tree(model, "clear it", List.of(), List.of()))));
        Path changed = delivery("tree-after", model, new Basket("trees",
                object(model, "TreeRecord", "keeper", "<Label>after</Label>"
                        + tree(model, "new one", List.of("first", "second", "first"), List.of("repeat", "repeat")))
                + object(model, "TreeRecord", "clear", "<Label>empty</Label>")));
        seed(db, referenceSchema, model, "structures", initial);
        try (var sql = db.connect()) {
            var before = rows(db, sql, "treerecord");
            var oldLeafIds = strings(sql, "SELECT t_id FROM " + db.table("leafvalue"));
            assertEquals(6, oldLeafIds.size());
            Path output = mutate(db, referenceSchema, "update", model, "structures", changed);
            var after = rows(db, sql, "treerecord");
            long owner = after.get(key("structures", "trees", "keeper")).object();
            assertEquals(before.get(key("structures", "trees", "keeper")).withLabel("after"),
                    after.get(key("structures", "trees", "keeper")));
            assertEquals(before.get(key("structures", "trees", "clear")).withLabel("empty"),
                    after.get(key("structures", "trees", "clear")));
            String root = scalar(sql, "SELECT t_id FROM " + db.table("outervalue") + " WHERE treerecord_detail=" + owner);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("outervalue")));
            assertEquals(List.of("new one"), strings(sql, "SELECT label FROM " + db.table("leafvalue") + " WHERE outervalue_one=" + root));
            assertEquals(List.of("first", "second", "first"), strings(sql, "SELECT label FROM " + db.table("leafvalue")
                    + " WHERE outervalue_chain=" + root + " ORDER BY t_seq"));
            assertEquals(List.of("repeat", "repeat"), strings(sql, "SELECT label FROM " + db.table("leafvalue")
                    + " WHERE outervalue_flags=" + root + " ORDER BY t_seq"));
            var newLeafIds = strings(sql, "SELECT t_id FROM " + db.table("leafvalue"));
            assertEquals(6, newLeafIds.size());
            assertTrue(newLeafIds.stream().noneMatch(oldLeafIds::contains));
            var objects = children(children(readDataSection(output, version)).getFirst());
            var keeper = objects.stream().filter(o -> "keeper".equals(id(o, version, "tid"))).findFirst().orElseThrow();
            assertEquals("new one", child(children(child(children(child(keeper, "Detail")).getFirst(), "One")).getFirst(),
                    "Label").getTextContent());
            var cleared = objects.stream().filter(o -> "clear".equals(id(o, version, "tid"))).findFirst().orElseThrow();
            assertNull(child(cleared, "Detail"));
        }
    }

    void updateLocalReferences(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutLocal" + version;
        schema(db, referenceSchema, model);
        String originalTarget = object(model, "MutationTarget", "former", "<Label>former</Label>");
        Path original = delivery("local-before", model, new Basket("local",
                originalTarget + recordWithReference(model, "record", "before", "former", "")
                + recordWithReference(model, "clear", "clear", "former", "")));
        String owner = recordWithReference(model, "record", "now", "fresh", "");
        String cleared = object(model, "MutationRecord", "clear", "<Label>empty</Label><Detail>"
                + structure(model, "MutationLink", "") + "</Detail>");
        Path changed = delivery("local-after", model, new Basket("local", owner + cleared
                + object(model, "MutationTarget", "fresh", "<Label>fresh</Label>")));
        seed(db, referenceSchema, model, "local", original);

        try (var sql = db.connect()) {
            var before = rows(db, sql, "mutationrecord");
            Path output = mutate(db, referenceSchema, "update", model, "local", changed);
            var after = rows(db, sql, "mutationrecord");
            assertEquals(before.get(key("local", "local", "record")).withLabel("now"),
                    after.get(key("local", "local", "record")));
            assertEquals(before.get(key("local", "local", "clear")).withLabel("empty"),
                    after.get(key("local", "local", "clear")));
            var targets = rows(db, sql, "mutationtarget");
            assertEquals(1, targets.size());
            String link = "SELECT destination FROM " + db.table("mutationlink") + " WHERE mutationrecord_detail = ";
            String targetId = Long.toString(targets.get(key("local", "local", "fresh")).object());
            assertEquals(targetId, scalar(sql, link + after.get(key("local", "local", "record")).object()));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("mutationlink")
                    + " WHERE destination IS NULL AND mutationrecord_detail=" + after.get(key("local", "local", "clear")).object()));

            var objects = children(children(readDataSection(output, version)).getFirst());
            var exported = objects.stream().filter(o -> "record".equals(id(o, version, "tid"))).findFirst().orElseThrow();
            var destination = child(children(child(exported, "Detail")).getFirst(), "Destination");
            assertEquals("fresh", id(destination, version, "ref"));

            Path unresolved = delivery("local-invalid", model, new Basket("local", owner.replace(">now<", ">bad<")));
            var failure = cli(db, "update", model, "local", unresolved, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("Cannot encode reference"), failure::diagnostics);
            assertEquals(after, rows(db, sql, "mutationrecord"));
            assertEquals(targets, rows(db, sql, "mutationtarget"));
            assertEquals(targetId, scalar(sql, link + after.get(key("local", "local", "record")).object()));
        }
    }

    void updateAssociations(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutRoles" + version;
        schema(db, referenceSchema, model);
        String proofId = identity("proof", true);
        String lost = identity("proof-removed", true);
        Path initial = delivery("roles-before", model, new Basket("roles",
                object(model, "RoleMarker", "first", "<Label>first</Label>" + review(model, "hub-a", "old"))
                + object(model, "RoleMarker", "second", "<Label>second</Label>" + review(model, "hub-a", "remove"))
                + object(model, "RoleHub", "hub-a", "<Label>a</Label>") + object(model, "RoleHub", "hub-b", "<Label>b</Label>")
                + visit(model, "first", "hub-a", "7") + visit(model, "second", "hub-b", "4")
                + proof(model, proofId, "first", "hub-a", "old") + proof(model, lost, "second", "hub-b", "remove")));
        Path changed = delivery("roles-after", model, new Basket("roles",
                proof(model, proofId, "second", "hub-b", "updated") + visit(model, "second", "hub-b", "17")
                + object(model, "RoleMarker", "first", "<Label>first</Label>" + review(model, "hub-b", "checked"))
                + object(model, "RoleMarker", "second", "<Label>second</Label>")
                + object(model, "RoleHub", "hub-a", "<Label>a</Label>") + object(model, "RoleHub", "hub-b", "<Label>b</Label>")));
        seed(db, referenceSchema, model, "roles", initial);

        try (var sql = db.connect()) {
            var markerRows = rows(db, sql, "rolemarker");
            var hubRows = rows(db, sql, "rolehub");
            var before = rows(db, sql, "roleproof");
            var previousVisits = strings(sql, "SELECT t_id FROM " + db.table("rolevisit"));
            assertEquals(2, previousVisits.size());
            Path output = mutate(db, referenceSchema, "update", model, "roles", changed);
            assertEquals(markerRows, rows(db, sql, "rolemarker"));
            assertEquals(hubRows, rows(db, sql, "rolehub"));
            var proofs = rows(db, sql, "roleproof");
            var proofKey = key("roles", "roles", proofId);
            assertEquals(Map.of(proofKey, before.get(proofKey).withLabel("updated")), proofs);

            long first = markerRows.get(key("roles", "roles", "first")).object();
            long second = markerRows.get(key("roles", "roles", "second")).object();
            long hub = hubRows.get(key("roles", "roles", "hub-b")).object();
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("roleproof")
                    + " WHERE proofmarker=" + second + " AND proofhub=" + hub));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("rolereview")));
            assertEquals("checked", scalar(sql, "SELECT memo FROM " + db.table("rolereview")
                    + " WHERE reviewowner=" + first + " AND reviewedat=" + hub));
            String positions = referenceSchema ? " = 17" : " IS NULL";
            String visitId = scalar(sql, "SELECT t_id FROM " + db.table("rolevisit")
                    + " WHERE markervisit=" + second + " AND hubvisit=" + hub + " AND markervisit_t_seq" + positions);
            assertNotNull(visitId);
            assertFalse(previousVisits.contains(visitId));
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("rolevisit")));

            var objects = children(children(readDataSection(output, version)).getFirst());
            var kept = objects.stream().filter(o -> proofId.equals(id(o, version, "tid"))).findFirst().orElseThrow();
            assertEquals("second", id(child(kept, "ProofMarker"), version, "ref"));
            assertEquals("hub-b", id(child(kept, "ProofHub"), version, "ref"));
            assertVisitPosition(output, "");
            if (referenceSchema) {
                // The standalone ORDERED positions are omitted by the 5.5.2 reference exporter.
                Path nativeOutput = work.resolve("ordered-update-native.xtf");
                assertCliSuccess(db, "export", model, "roles", nativeOutput);
                assertVisitPosition(nativeOutput, "17");
            }
        }
    }

    private void assertVisitPosition(Path output, String position) throws Exception {
        var visit = children(children(readDataSection(output, version)).getFirst()).stream()
                .filter(o -> o.getLocalName().endsWith("RoleVisit")).findFirst().orElseThrow();
        assertEquals(position, id(child(visit, "MarkerVisit"), version, "order_pos"));
    }

    private String review(String model, String hub, String memo) {
        String ref = version == 23 ? "REF" : "i:ref";
        return "<ReviewedAt " + ref + "=\"" + hub + "\">"
                + structure(model, "RoleReview", "<Memo>" + memo + "</Memo>") + "</ReviewedAt>";
    }

    private String visit(String model, String marker, String hub, String position) {
        String ordered = version == 23 ? "<MarkerVisit REF=\"%s\" ORDER_POS=\"%s\"/>".formatted(marker, position)
                : "<MarkerVisit i:ref=\"%s\" i:order_pos=\"%s\"/>".formatted(marker, position);
        return structure(model, "RoleVisit", ordered + reference("HubVisit", hub, ""));
    }

    private String proof(String model, String tid, String marker, String hub, String label) {
        return object(model, "RoleProof", tid, reference("ProofMarker", marker, "") + reference("ProofHub", hub, "")
                + "<Label>" + label + "</Label>");
    }

    void updateExternalReferences(TestDatabase db, boolean referenceSchema) throws Exception {
        String model = "DbMutRef" + version;
        schema(db, referenceSchema, model);
        Path catalog = delivery("update-catalog", model, new Basket("catalog", target(model, "destination", "listed")));
        Path initial = delivery("external-before", model, new Basket("source",
                        recordWithReference(model, "external", "old", "destination", "catalog")),
                new Basket("spare", target(model, "untouched", "leave")),
                new Basket("sinks", target(model, "retain", "old target") + target(model, "drop", "unused")));
        Path consumer = delivery("external-consumer", model, new Basket("consumer",
                recordWithReference(model, "consumer-record", "separate dataset", "retain", "sinks")));
        String source = recordWithReference(model, "external", "new", "destination", "catalog")
                + recordWithReference(model, "forward", "later", "fresh", "sinks")
                + recordWithReference(model, "spare-ref", "from omitted basket", "untouched", "spare");
        Path changed = delivery("external-after", model, new Basket("source", source),
                new Basket("sinks", target(model, "retain", "updated target") + target(model, "fresh", "new")));
        seed(db, referenceSchema, model, "catalog", catalog);
        seed(db, referenceSchema, model, "main", initial);
        seed(db, referenceSchema, model, "consumer", consumer);

        try (var sql = db.connect()) {
            var before = rows(db, sql, "mutationtarget");
            var externalBefore = rows(db, sql, "mutationrecord").get(key("main", "source", "external"));
            Path output = mutate(db, referenceSchema, "update", model, "main", changed);
            var after = rows(db, sql, "mutationtarget");
            var records = rows(db, sql, "mutationrecord");
            var retain = key("main", "sinks", "retain");
            var outside = key("catalog", "catalog", "destination");
            var spare = key("main", "spare", "untouched");
            assertEquals(before.get(retain).withLabel("updated target"), after.get(retain));
            assertEquals(before.get(outside), after.get(outside));
            assertEquals(before.get(spare), after.get(spare));
            assertNull(after.get(key("main", "sinks", "drop")));
            assertEquals(externalBefore.withLabel("new"), records.get(key("main", "source", "external")));
            assertDestination(db, sql, records.get(key("main", "source", "forward")), after.get(key("main", "sinks", "fresh")));
            assertDestination(db, sql, records.get(key("main", "source", "spare-ref")), after.get(spare));
            assertDestination(db, sql, records.get(key("main", "source", "external")), after.get(outside));
            assertDestination(db, sql, records.get(key("consumer", "consumer", "consumer-record")), after.get(retain));
            var exported = children(readDataSection(output, version)).stream().flatMap(b -> children(b).stream())
                    .filter(o -> "forward".equals(id(o, version, "tid"))).findFirst().orElseThrow();
            assertEquals("fresh", id(child(children(child(exported, "Detail")).getFirst(), "Destination"), version, "ref"));

            Path noTarget = delivery("external-remove-protected", model, new Basket("sinks", target(model, "fresh", "new")));
            var failure = cli(db, "update", model, "main", noTarget, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("Cannot delete referenced object T_Id " + after.get(retain).object()),
                    failure::diagnostics);
            assertEquals(after, rows(db, sql, "mutationtarget"));
            assertEquals(records, rows(db, sql, "mutationrecord"));

            Path duplicate = delivery("duplicate-catalog", model, new Basket("other-catalog", target(model, "destination", "duplicate")));
            assertCliSuccess(db, "import", model, "duplicate", duplicate);
            Path ambiguous = delivery("ambiguous-update-ref", model, new Basket("source",
                    recordWithReference(model, "external", "ambiguous", "destination", "")));
            failure = cli(db, "update", model, "main", ambiguous, CancellationToken.none());
            assertNotEquals(0, failure.code(), failure::diagnostics);
            assertTrue(failure.diagnostics().contains("Ambiguous stored reference target destination"), failure::diagnostics);
            assertEquals(records, rows(db, sql, "mutationrecord"));
            assertDestination(db, sql, records.get(key("main", "source", "external")), after.get(outside));

            Path removeBoth = delivery("external-remove-source-and-target", model, new Basket("source", ""),
                    new Basket("sinks", target(model, "retain", "updated target")));
            assertCliSuccess(db, "update", model, "main", removeBoth);
            assertNull(rows(db, sql, "mutationtarget").get(key("main", "sinks", "fresh")));
            var survivingRecords = rows(db, sql, "mutationrecord");
            assertEquals(Map.of(key("consumer", "consumer", "consumer-record"),
                    records.get(key("consumer", "consumer", "consumer-record"))), survivingRecords);
            assertDestination(db, sql, survivingRecords.get(key("consumer", "consumer", "consumer-record")), after.get(retain));
        }
    }

    private String target(String model, String tid, String label) {
        return object(model, "MutationTarget", tid, "<Label>" + label + "</Label>");
    }

    private static void assertDestination(TestDatabase db, Connection sql, Row owner, Row target) throws SQLException {
        assertEquals(Long.toString(target.object()), scalar(sql, "SELECT destination FROM " + db.table("mutationlink")
                + " WHERE mutationrecord_detail = " + owner.object()));
    }

    private String tree(String model, String one, List<String> chain, List<String> flags) {
        return "<Detail>" + structure(model, "OuterValue", leaves(model, "One", List.of(one))
                + leaves(model, "Chain", chain) + leaves(model, "Flags", flags)) + "</Detail>";
    }

    private String leaves(String model, String field, List<String> values) {
        if (values.isEmpty()) return "";
        var xml = new StringBuilder("<").append(field).append(">");
        for (String value : values) xml.append(structure(model, "LeafValue", "<Label>" + value + "</Label>"));
        return xml.append("</").append(field).append(">").toString();
    }

    private static List<String> strings(Connection sql, String query) throws SQLException {
        var values = new ArrayList<String>();
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) values.add(rows.getString(1));
        }
        return values;
    }

    private void checkStoredIdentities(TestDatabase db, Connection sql, String model, Path input,
                                       Map<List<String>, Row> after) throws Exception {
        assertCliSuccess(db, "update", model, "main", input);
        var replayed = rows(db, sql, "mutationitem");
        replayed.remove(key("fresh", "fresh", "fresh-item"));
        assertEquals(after, replayed);

        String items = db.table("mutationitem");
        long added = after.get(key("main", "main", "added")).object();
        execute(sql, "UPDATE " + items + " SET t_ili_tid='kept' WHERE t_id=" + added);
        var failure = cli(db, "update", model, "main", input, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        assertTrue(failure.diagnostics().contains("Ambiguous stored object TID kept"), failure::diagnostics);
        execute(sql, "UPDATE " + items + " SET t_ili_tid='added' WHERE t_id=" + added);

        String baskets = db.table("t_ili2db_basket");
        long foreign = after.get(key("other", "foreign", "their-item")).basket();
        execute(sql, "UPDATE " + baskets + " SET t_ili_tid='main' WHERE t_id=" + foreign);
        failure = cli(db, "update", model, "main", input, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        assertTrue(failure.diagnostics().contains("Ambiguous stored BID main"), failure::diagnostics);
        execute(sql, "UPDATE " + baskets + " SET t_ili_tid='foreign' WHERE t_id=" + foreign);

        long untouch = after.get(key("main", "untouched", "untouched-item")).basket();
        execute(sql, "UPDATE " + baskets + " SET t_ili_tid=NULL WHERE t_id=" + untouch);
        Path unknown = delivery("unknown-basket", model, new Basket("cannot-recover", item(model, "new", "new")));
        failure = cli(db, "update", model, "main", unknown, CancellationToken.none());
        assertNotEquals(0, failure.code(), failure::diagnostics);
        assertTrue(failure.diagnostics().contains("Missing stored BID"), failure::diagnostics);
        execute(sql, "UPDATE " + baskets + " SET t_ili_tid='untouched' WHERE t_id=" + untouch);
        var finalRows = rows(db, sql, "mutationitem");
        finalRows.remove(key("fresh", "fresh", "fresh-item"));
        assertEquals(after, finalRows);
    }

    private static String identity(String name, boolean stable) {
        return stable ? UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString() : name;
    }

    private void checkConcurrentWriter(TestDatabase db, String model, Path input) throws Exception {
        try (var lock = db.connect(); var statement = lock.createStatement()) {
            if (db.postgis()) {
                lock.setAutoCommit(false);
                statement.execute("LOCK TABLE " + db.table("mutationitem") + " IN ROW EXCLUSIVE MODE");
            } else {
                statement.execute("BEGIN IMMEDIATE");
            }
            try {
                var failure = cli(db, "replace", model, "main", input, CancellationToken.none());
                assertNotEquals(0, failure.code(), failure::diagnostics);
                if (db.postgis()) assertTrue(failure.diagnostics().contains("Another writer"), failure::diagnostics);
            } finally {
                if (db.postgis()) lock.rollback();
                else statement.execute("ROLLBACK");
            }
        }
    }

    private void schema(TestDatabase db, boolean referenceSchema, String model) throws Exception {
        Path ili = work.resolve(model + ".ili");
        if (Files.notExists(ili)) {
            try (var resource = DatasetWriteCases.class.getResourceAsStream("/" + ili.getFileName())) {
                Files.copy(resource, ili);
            }
        }
        if (referenceSchema) {
            reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                    "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", ili.toString());
        } else {
            assertSchemaWithIdentityColumns(db, model, ili);
        }
    }

    private void runReference(TestDatabase db, String operation, String dataset, Path transfer) throws Exception {
        reference.run(db, work.toString(), "--" + operation, "--importTid", "--importBid", "--dataset", dataset,
                transfer.toString());
    }

    private void seed(TestDatabase db, boolean referenceSchema, String model, String dataset, Path transfer) throws Exception {
        if (referenceSchema) runReference(db, "import", dataset, transfer);
        else assertCliSuccess(db, "import", model, dataset, transfer);
    }

    private Path mutate(TestDatabase db, boolean nativeMutation, String operation, String model, String dataset,
                        Path transfer) throws Exception {
        Path out = work.resolve(operation + "-mixed.xtf");
        Files.deleteIfExists(out);
        if (nativeMutation) {
            assertCliSuccess(db, operation, model, dataset, transfer);
            reference.run(db, work.toString(), "--export", "--exportTid", "--dataset", dataset, out.toString());
        } else {
            runReference(db, operation, dataset, transfer);
            assertCliSuccess(db, "export", model, dataset, out);
        }
        return out;
    }

    private Path delivery(String file, String model, Basket... baskets) throws Exception {
        var first = baskets[0];
        String xml = referenceDatasetFixture(model, version, first.bid(), first.objects());
        if (model.equals("DbInterop23")) xml = xml.replace("VERSION=\"1\"", "VERSION=\"2026-11-20\"");
        String end = version == 23 ? "</DATASECTION>" : "</i:datasection>";
        var extra = new StringBuilder();
        for (int i = 1; i < baskets.length; i++) {
            var basket = baskets[i];
            extra.append(version == 23 ? "<%1$s.Data BID=\"%2$s\">%3$s</%1$s.Data>".formatted(model, basket.bid(), basket.objects())
                    : "<Data i:bid=\"%s\">%s</Data>".formatted(basket.bid(), basket.objects()));
        }
        if (version == 24) {
            xml = xml.replace("<i:transfer ", "<i:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        }
        return Files.writeString(work.resolve(file + ".xtf"), xml.replace(end, extra + end));
    }

    private String item(String model, String tid, String label) {
        return object(model, "MutationItem", tid, "<Label>" + label + "</Label>");
    }

    private String structure(String model, String type, String values) {
        String tag = version == 23 ? model + ".Data." + type : type;
        return "<%1$s>%2$s</%1$s>".formatted(tag, values);
    }

    private String reference(String name, String tid, String bid) {
        if (version == 24) return "<%s i:ref=\"%s\"/>".formatted(name, tid);
        String targetBasket = bid.isEmpty() ? "" : " BID=\"%s\"".formatted(bid);
        return "<%s REF=\"%s\"%s/>".formatted(name, tid, targetBasket);
    }

    private String object(String model, String type, String tid, String values) {
        return version == 23 ? "<%1$s.Data.%2$s TID=\"%3$s\">%4$s</%1$s.Data.%2$s>".formatted(model, type, tid, values)
                : "<%1$s i:tid=\"%2$s\">%3$s</%1$s>".formatted(type, tid, values);
    }

    private void assertSingleExport(Path path, String bid, String tid, String label) throws Exception {
        var baskets = children(readDataSection(path, version));
        assertEquals(1, baskets.size());
        assertEquals(bid, id(baskets.getFirst(), version, "bid"));
        var objects = children(baskets.getFirst());
        assertEquals(1, objects.size());
        assertEquals(tid, id(objects.getFirst(), version, "tid"));
        assertEquals(label, child(objects.getFirst(), "Label").getTextContent());
    }

    private static Map<List<String>, Row> rows(TestDatabase db, Connection sql, String table) throws SQLException {
        var result = new HashMap<List<String>, Row>();
        String query = """
                SELECT d.datasetname, b.t_ili_tid, i.t_ili_tid, d.t_id, b.t_id, i.t_id, i.label
                FROM %s i JOIN %s b ON b.t_id = i.t_basket JOIN %s d ON d.t_id = b.dataset
                """.formatted(db.table(table), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"));
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            while (rows.next()) {
                var key = key(rows.getString(1), rows.getString(2), rows.getString(3));
                assertNull(result.put(key, new Row(rows.getLong(4), rows.getLong(5), rows.getLong(6), rows.getString(7))),
                        key.toString());
            }
        }
        return result;
    }

    private static List<String> key(String dataset, String bid, String tid) { return List.of(dataset, bid, tid); }

    private record Basket(String bid, String objects) { }
    private record Row(long dataset, long basket, long object, String label) {
        Row withLabel(String value) { return new Row(dataset, basket, object, value); }
    }
}
