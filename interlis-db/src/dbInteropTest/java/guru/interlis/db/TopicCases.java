package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        var basket = assertRegion(file, model, prefix + "-b");
        var station = assertObject(basket, model, "Region", "LocalStation", prefix + "-s");
        var memo = child(station, "Memo");
        var local = child(station, "LocalMemo");
        assertNamespace(memo, parent);
        assertNamespace(local, model);
        assertEquals("base", memo.getTextContent());
        assertEquals("child", local.getTextContent());
        assertEquals("inherited", child(assertObject(basket, parent, "Core", "Marker", prefix + "-m"), "Memo").getTextContent());
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
        reference.run(baseline, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol",
                "--createTidCol", "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", model);
        run(own, "schemaimport", model, null, derived, "--model-file", base.toString());
        try (var refSql = baseline.connect(); var nativeSql = own.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(nativeSql, own.postgis(), own.schema()));
        }
        Files.delete(base);
        Files.delete(derived);
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
