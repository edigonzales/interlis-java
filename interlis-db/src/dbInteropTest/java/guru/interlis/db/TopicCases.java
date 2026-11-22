package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbInteropTest.assertCliFailure;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TopicCases {
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    TopicCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void inherited(TestDatabase baseline, TestDatabase own) throws Exception {
        String parent = "TownParent" + version;
        String model = "TownFlat" + version;
        schemas(baseline, own, parent, model);
        var input = fixture("flat" + version + ".xtf");
        var output = work.resolve("flat-out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", input);
            run(db, "export", model, "legacy", output);
            assertInherited(output, parent, model, "flat");
            var nativeInput = Files.writeString(work.resolve("flat-native.xtf"), Files.readString(input).replace("flat-", "own-"));
            run(db, "import", model, "own", nativeInput);
            referenceExport(db, "own", output);
            assertInherited(output, parent, model, "own");
            try (var sql = db.connect()) {
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_classname")
                        + " WHERE iliname LIKE '" + parent + ".Other.%'"));
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("marker") + " m JOIN "
                        + db.table("desk") + " d ON d.t_basket=m.t_basket JOIN " + db.table("t_ili2db_basket")
                        + " b ON b.t_id=m.t_basket WHERE b.topic='" + model + ".Region' AND m.memo='inherited'"));
            }
        }
    }

    void fragments(TestDatabase baseline, TestDatabase own) throws Exception {
        String parent = "TownParent" + version;
        String model = "TownChild" + version;
        schemas(baseline, own, parent, model);
        var input = fixture("region" + version + ".xtf");
        var nativeInput = Files.writeString(work.resolve("region-native.xtf"),
                Files.readString(input).replace("region-", "native-"));
        var output = work.resolve("region-out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", input);
            run(db, "export", model, "legacy", output);
            assertFragments(output, parent, model, "region");
            run(db, "import", model, "own", nativeInput);
            referenceExport(db, "own", output);
            assertFragments(output, parent, model, "native");
            try (var sql = db.connect()) {
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station") + " s JOIN "
                        + db.table("localstation") + " l ON l.t_id=s.t_id AND l.t_basket=s.t_basket JOIN "
                        + db.table("t_ili2db_basket") + " b ON b.t_id=s.t_basket WHERE b.topic='" + model
                        + ".Region' AND s.t_type='localstation' AND s.memo='base' AND l.localmemo='child'"));
                execute(sql, "UPDATE " + db.table("station") + " SET t_type='siblingstation' WHERE t_ili_tid='native-s'");
            }
            String previous = Files.readString(output);
            var result = cli(db, "export", model, "own", output, CancellationToken.none());
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("does not belong to basket topic " + model + ".Region"),
                    result::diagnostics);
            assertEquals(previous, Files.readString(output));
        }
    }

    private void assertFragments(Path file, String parent, String model, String prefix) throws Exception {
        assertFragments(file, parent, model, prefix, "inherited", "base", "child");
    }

    private void assertFragments(Path file, String parent, String model, String prefix, String inheritedMemo,
                                 String baseMemo, String localMemo) throws Exception {
        var basket = assertRegion(file, model, prefix + "-b");
        var station = assertObject(basket, model, "Region", "LocalStation", prefix + "-s");
        var memo = child(station, "Memo");
        var local = child(station, "LocalMemo");
        assertNamespace(memo, parent);
        assertNamespace(local, model);
        assertEquals(baseMemo, memo.getTextContent());
        assertEquals(localMemo, local.getTextContent());
        assertEquals(inheritedMemo, child(assertObject(basket, parent, "Core", "Marker", prefix + "-m"), "Memo").getTextContent());
    }

    void refinement(TestDatabase baseline, TestDatabase own) throws Exception {
        String parent = "AbstractTown" + version;
        String model = "RefinedTown" + version;
        schemas(baseline, own, parent, model);
        var input = fixture("refine" + version + ".xtf");
        var nativeInput = Files.writeString(work.resolve("refine-native.xtf"),
                Files.readString(input).replace("refine-", "native-").replace(">text<", ">updated<"));
        var output = work.resolve("refine-out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", input);
            run(db, "export", model, "legacy", output);
            assertRefinement(output, parent, model, "refine", "text");
            run(db, "import", model, "own", nativeInput);
            referenceExport(db, "own", output);
            assertRefinement(output, parent, model, "native", "updated");
            try (var sql = db.connect()) {
                String attr = db.table("t_ili2db_attrname");
                assertEquals("label", scalar(sql, "SELECT sqlname FROM " + attr + " WHERE iliname='"
                        + parent + ".Core.Item.Label' AND colowner='item'"));
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + attr + " WHERE iliname='"
                        + model + ".Region.Item.Label'"));
                String refined = scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname")
                        + " WHERE iliname='" + model + ".Region.Item'");
                assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table("item") + " i JOIN "
                        + db.table(refined) + " r ON r.t_id=i.t_id AND r.t_basket=i.t_basket"
                        + " WHERE i.t_ili_tid='native-i' AND i.t_type='" + refined
                        + "' AND i.label='updated' AND i.asize=4 AND r.extra='added'"));
                String baskets = db.table("t_ili2db_basket");
                execute(sql, "INSERT INTO " + baskets + " (t_id, topic, t_ili_tid, attachmentkey, dataset)"
                        + " SELECT 100000, '" + parent + ".Core', 'abstract-b', 'abstract-b', dataset FROM " + baskets
                        + " WHERE t_ili_tid='native-b'");
            }
            String previous = Files.readString(output);
            var result = cli(db, "export", model, "own", output, CancellationToken.none(), "--model", parent);
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("Basket uses an abstract topic: " + parent + ".Core"),
                    result::diagnostics);
            assertEquals(previous, Files.readString(output));
        }
    }

    private void assertRefinement(Path file, String parent, String model, String prefix, String text) throws Exception {
        var basket = assertRegion(file, model, prefix + "-b");
        var item = assertObject(basket, model, "Region", "Item", prefix + "-i");
        var label = child(item, "Label");
        var size = child(item, "Size");
        var extra = child(item, "Extra");
        assertNamespace(label, parent);
        assertNamespace(size, parent);
        assertNamespace(extra, model);
        assertEquals(text, label.getTextContent());
        assertEquals("4", size.getTextContent());
        assertEquals("added", extra.getTextContent());
        assertObject(basket, parent, "Core", "Marker", prefix + "-m");
    }

    void references(TestDatabase baseline, TestDatabase own) throws Exception {
        String parent = "LinksBase" + version;
        String model = "LinksTown" + version;
        schemas(baseline, own, parent, model);
        var input = fixture("links" + version + ".xtf");
        var source = Files.readString(input);
        var nativeInput = Files.writeString(work.resolve("links-native.xtf"), source.replace("links-", "native-"));
        String depot = version == 23 ? "<LinksBase23.Core.Depot TID=\"bad-depot\"/>" : "<p:Depot i:tid=\"bad-depot\"/>";
        String region = version == 23 ? "</LinksTown23.Region>" : "</d:Region>";
        String sibling = version == 23 ? "<LinksTown23.Sibling BID=\"bad-sibling\">" + depot + "</LinksTown23.Sibling>"
                : "<d:Sibling i:bid=\"bad-sibling\">" + depot + "</d:Sibling>";
        var bad = Files.writeString(work.resolve("links-wrong-basket.xtf"), source.replace("links-", "bad-")
                .replace(depot, "").replace(region, region + "\n" + sibling));
        var output = work.resolve("links-out.xtf");
        for (var db : List.of(baseline, own)) {
            referenceImport(db, "legacy", input);
            run(db, "export", model, "legacy", output);
            assertReferences(output, parent, model, "links");
            run(db, "import", model, "own", nativeInput);
            referenceExport(db, "own", output);
            assertReferences(output, parent, model, "native");
            var result = cli(db, "import", model, "wrong-basket", bad, CancellationToken.none());
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("to bad-depot as an ID in the mapped target table in this basket"),
                    result::diagnostics);
            try (var sql = db.connect()) {
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note") + " n JOIN "
                        + db.table("delivery") + " d ON d.t_id=n.delivery_details AND d.t_basket=n.t_basket JOIN "
                        + db.table("depot") + " p ON p.t_id=n.receivingdepot AND p.t_id=d.deliveredat AND p.t_basket=n.t_basket JOIN "
                        + db.table("crate") + " c ON c.stockedat=p.t_id AND c.t_basket=p.t_basket JOIN "
                        + db.table("registerentry") + " e ON e.t_id=n.entry AND e.t_basket<>n.t_basket"));
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note")));
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='wrong-basket'"));
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid LIKE 'bad-%'"));
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("delivery") + " WHERE t_ili_tid LIKE 'bad-%'"));
            }
        }
    }

    private void assertReferences(Path file, String parent, String model, String prefix) throws Exception {
        var basket = assertRegion(file, model, prefix + "-b");
        var delivery = assertObject(basket, model, "Region", "Delivery", prefix + "-delivery");
        var crate = assertObject(basket, parent, "Core", "Crate", prefix + "-crate");
        assertObject(basket, parent, "Core", "Depot", prefix + "-depot");
        var note = children(child(delivery, "Details")).getFirst();
        assertEquals(prefix + "-depot", id(child(note, "ReceivingDepot"), version, "ref"));
        assertEquals(prefix + "-depot", id(child(delivery, "DeliveredAt"), version, "ref"));
        assertEquals(prefix + "-depot", id(child(crate, "StockedAt"), version, "ref"));
        var registry = children(readDataSection(file, version)).getLast();
        assertName(registry, model, "Registry");
        assertEquals(prefix + "-registry", id(registry, version, "bid"));
        var target = assertObject(registry, model, "Registry", "RegisterEntry", prefix + "-entry");
        assertEquals(id(target, version, "tid"), id(child(note, "Entry"), version, "ref"));
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
            assertFragments(output, parent, model, "region", "new marker", "new base", "new child");

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
            assertFragments(output, parent, model, "region", "new marker", "new base", "new child");

            run(db, "delete", incoming, "incoming", null);
            run(db, "replace", model, "inherited", input);
            assertEquals(oldDataset, scalar(sql, dataset));
            assertNotEquals(oldStation, scalar(sql, ownStation));
            assertEquals(scalar(sql, ownStation), scalar(sql, fragment));
            referenceExport(db, "inherited", output);
            assertFragments(output, parent, model, "region");
            run(db, "delete", model, "inherited", null);
            assertEquals("independent", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            for (var table : List.of("station", "localstation", "marker", "t_ili2db_basket")) {
                assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)), table);
            }
            referenceExport(db, "independent", output);
            assertFragments(output, parent, model, "independent");
        }
    }

    private String updatedTextWithoutStation(String source) {
        return source.lines().filter(line -> !line.contains("region-s")).collect(java.util.stream.Collectors.joining("\n"))
                .replace(">inherited<", ">must roll back<");
    }

    private void assertInherited(Path file, String parent, String model, String prefix) throws Exception {
        var basket = assertRegion(file, model, prefix + "-b");
        var marker = assertObject(basket, parent, "Core", "Marker", prefix + "-m");
        var desk = assertObject(basket, model, "Region", "Desk", prefix + "-d");
        assertEquals("inherited", child(marker, "Memo").getTextContent());
        assertEquals("local", child(desk, "LocalMemo").getTextContent());
    }

    private Element assertRegion(Path file, String model, String bid) throws Exception {
        var data = readDataSection(file, version);
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        var models = children(child(header, version == 23 ? "MODELS" : "models"));
        assertEquals(List.of(model), models.stream().map(m -> version == 23 ? m.getAttribute("NAME")
                : m.getTextContent().strip()).toList());
        var basket = children(data).getFirst();
        assertName(basket, model, "Region");
        assertEquals(bid, id(basket, version, "bid"));
        return basket;
    }

    private Element assertObject(Element basket, String model, String topic, String name, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertName(object, model, topic + "." + name);
        return object;
    }

    private void assertName(Element object, String model, String local) {
        assertEquals(version == 23 ? model + "." + local : local.substring(local.lastIndexOf('.') + 1),
                object.getLocalName());
        assertNamespace(object, model);
    }

    private void assertNamespace(Element element, String model) {
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, element.getNamespaceURI());
    }

    private void schemas(TestDatabase baseline, TestDatabase own, String parent, String model) throws Exception {
        var base = fixture(parent + ".ili");
        var derived = fixture(model + ".ili");
        referenceSchema(baseline, model);
        run(own, "schemaimport", model, null, derived, "--model-file", base.toString());
        try (var refSql = baseline.connect(); var nativeSql = own.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(nativeSql, own.postgis(), own.schema()));
        }
        Files.delete(base);
        Files.delete(derived);
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
