package guru.interlis.db;

import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliFailure;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.ModelFixtures.assertStructures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelCases {
    private static final String STORED_MODELS = "%ILI_FROM_DB";
    private final Path work;
    private final ReferenceTool reference;
    private final int version;

    ModelCases(Path work, ReferenceTool reference, int version) {
        this.work = work;
        this.reference = reference;
        this.version = version;
    }

    void importedTopics(TestDatabase baseline, TestDatabase nativeSchema) throws Exception {
        String model = "DbReachTopic" + version;
        String library = "DbImportedTarget" + version;
        var modelFile = fixture(model + ".ili");
        var libraryFile = fixture(library + ".ili");
        createReference(baseline, model, modelFile);
        var schema = schemaWithIdentityColumns(nativeSchema, model, modelFile, CancellationToken.none(),
                "--model-file", libraryFile.toString());
        assertEquals(0, schema.code(), schema::diagnostics);
        try (var refSql = baseline.connect(); var sql = nativeSchema.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, nativeSchema.postgis(), nativeSchema.schema()));
        }
        Files.delete(modelFile);
        Files.delete(libraryFile);
        var target = fixture(library + ".xtf");
        var input = fixture(model + ".xtf");
        var own = Files.writeString(work.resolve("own-topic.xtf"), Files.readString(input).replace("fixture-", "own-"));
        var duplicate = Files.writeString(work.resolve("duplicate-catalog.xtf"),
                Files.readString(target).replace("target-basket", "duplicate-basket"));
        var rejected = Files.writeString(work.resolve("ambiguous-topic.xtf"), Files.readString(input)
                .replace("fixture-", "ambiguous-").replace(" BID=\"target-basket\"", ""));
        for (var db : List.of(baseline, nativeSchema)) {
            referenceImport(db, "catalog", target);
            referenceImport(db, "legacy", input);
            Path output = work.resolve("legacy-topic.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertTopicDelivery(output, model, "fixture");

            assertCliSuccess(db, "import", model, "native", own);
            referenceExport(db, "native", output);
            assertTopicDelivery(output, model, "own");
            referenceImport(db, "catalog-copy", duplicate);
            try (var sql = db.connect()) {
                String metadata = db.table("t_ili2db_classname");
                assertEquals(library + ".Catalog.Marker", scalar(sql, "SELECT iliname FROM " + metadata + " WHERE sqlname='marker'"));
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + metadata + " WHERE iliname LIKE '" + library + ".Unused.%'"));
                String query = "SELECT COUNT(*) FROM " + db.table("report") + " r JOIN " + db.table("note")
                        + " n ON n.report_details=r.t_id JOIN " + db.table("entry")
                        + " e ON e.t_id=r.site AND e.t_id=n.destination JOIN " + db.table("t_ili2db_basket")
                        + " b ON e.t_basket=b.t_id WHERE e.t_ili_tid='target-entry' AND b.t_ili_tid='target-basket'";
                assertEquals("2", scalar(sql, query));
                var failed = cli(db, "import", model, "ambiguous", rejected, CancellationToken.none());
                assertEquals(1, failed.code(), failed::diagnostics);
                assertTrue(failed.diagnostics().contains("Ambiguous stored reference target target-entry"), failed::diagnostics);
                assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_dataset")
                        + " WHERE datasetname='ambiguous'"));
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("report")));
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("note")));
            }
        }
    }

    private void assertTopicDelivery(Path file, String model, String prefix) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(model), headerModels(data));
        assertEquals(1, children(data).size());
        var basket = children(data).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var report = children(basket).getFirst();
        assertEquals(prefix + "-report", id(report, version, "tid"));
        assertEquals("target-entry", id(child(report, "Site"), version, "ref"));
        var note = children(child(report, "Details")).getFirst();
        assertEquals("target-entry", id(child(note, "Destination"), version, "ref"));
    }

    void jointSchema(TestDatabase baseline, TestDatabase nativeSchema) throws Exception {
        String warehouse = "DbWarehouse" + version;
        String workshop = "DbWorkshop" + version;
        var first = fixture(warehouse + ".ili");
        var second = fixture(workshop + ".ili");
        createReference(baseline, warehouse + ";" + workshop);
        var created = schemaWithIdentityColumns(nativeSchema, warehouse, first, CancellationToken.none(),
                "--model", workshop, "--model-file", second.toString());
        assertEquals(0, created.code(), created::diagnostics);
        Files.delete(first);
        Files.delete(second);
        var inputWarehouse = fixture(warehouse + ".xtf");
        var inputWorkshop = fixture(workshop + ".xtf");
        for (var db : List.of(baseline, nativeSchema)) {
            String warehouseTable = tableFor(db, warehouse + ".Data.Entry");
            String workshopTable = tableFor(db, workshop + ".Data.Entry");
            assertTrue(Set.of(warehouseTable, workshopTable).contains("entry"));
            assertTrue(warehouseTable.equals("entry") || warehouseTable.startsWith("dbwarehouse" + version));
            assertTrue(workshopTable.equals("entry") || workshopTable.startsWith("dbworkshop" + version));
            singleModelExchange(db, warehouse, "warehouse", inputWarehouse);
            singleModelExchange(db, workshop, "workshop", inputWorkshop);
            try (var sql = db.connect()) {
                String ids = String.join(" UNION ALL ", List.of("t_ili2db_dataset", "t_ili2db_basket",
                                warehouseTable, workshopTable).stream()
                        .map(table -> "SELECT t_id FROM " + db.table(table)).toList());
                assertEquals("12", scalar(sql, "SELECT COUNT(DISTINCT t_id) FROM (" + ids + ") ids"));
            }
        }
    }

    private void singleModelExchange(TestDatabase db, String model, String value, Path input) throws Exception {
        referenceImport(db, "legacy-" + value, input);
        var output = work.resolve("single-model-output.xtf");
        assertCliSuccess(db, "export", model, "legacy-" + value, output);
        assertEntryDelivery(output, model, value, "fixture");
        var own = Files.writeString(work.resolve("single-model-input.xtf"), Files.readString(input).replace("fixture-", "own-"));
        assertCliSuccess(db, "import", model, "native-" + value, own);
        referenceExport(db, "native-" + value, output);
        assertEntryDelivery(output, model, value, "own");
    }

    void mixedExport(TestDatabase db) throws Exception {
        String warehouse = "DbWarehouse" + version;
        String workshop = "DbWorkshop" + version;
        int otherVersion = version == 23 ? 24 : 23;
        String other = "DbWarehouse" + otherVersion;
        var first = fixture(warehouse + ".ili");
        var second = fixture(workshop + ".ili");
        var unrelated = fixture(other + ".ili");
        createReference(db, warehouse + ";" + workshop);
        createReference(db, other, unrelated);
        for (var source : List.of(first, second, unrelated)) Files.delete(source);
        referenceImport(db, "mixed", fixture("DbMixedModels" + version + ".xtf"));
        referenceImport(db, "other-version", fixture(other + ".xtf"));
        referenceImport(db, "warehouse-only", fixture(warehouse + ".xtf"));

        var output = work.resolve("mixed-export.xtf");
        var exported = cli(db, "export", warehouse, "mixed", output, CancellationToken.none(), "--model", workshop);
        assertEquals(0, exported.code(), exported::diagnostics);
        assertMixedDelivery(output, warehouse, workshop, "mixed", "warehouse");
        String published = Files.readString(output);
        var incomplete = cli(db, "export", warehouse, "mixed", output, CancellationToken.none());
        assertEquals(1, incomplete.code(), incomplete::diagnostics);
        assertTrue(incomplete.diagnostics().contains("outside the selected model: " + workshop + ".Data"),
                incomplete::diagnostics);
        assertEquals(published, Files.readString(output));
        var mixedVersions = cli(db, "export", warehouse, "mixed", output, CancellationToken.none(), "--model", other);
        assertEquals(1, mixedVersions.code(), mixedVersions::diagnostics);
        assertTrue(mixedVersions.diagnostics().contains("same INTERLIS language version"), mixedVersions::diagnostics);
        assertEquals(published, Files.readString(output));

        exported = cli(db, "export", warehouse, "warehouse-only", output, CancellationToken.none(), "--model", workshop);
        assertEquals(0, exported.code(), exported::diagnostics);
        assertEntryDelivery(output, warehouse, "warehouse", "fixture");
        assertCliSuccess(db, "export", other, "other-version", output);
        new ModelCases(work, reference, otherVersion).assertEntryDelivery(output, other, "warehouse", "fixture");
    }

    void mixedImport(TestDatabase baseline, TestDatabase nativeSchema) throws Exception {
        String warehouse = "DbWarehouse" + version;
        String workshop = "DbWorkshop" + version;
        var first = fixture(warehouse + ".ili");
        var second = fixture(workshop + ".ili");
        createReference(baseline, warehouse + ";" + workshop);
        var created = schemaWithIdentityColumns(nativeSchema, warehouse, first, CancellationToken.none(),
                "--model", workshop, "--model-file", second.toString());
        assertEquals(0, created.code(), created::diagnostics);
        Files.delete(first);
        Files.delete(second);
        var input = fixture("DbMixedModels" + version + ".xtf");
        String fixture = Files.readString(input);
        String outside = version == 23 ? fixture.replace("<" + workshop + ".Data BID", "<DbOutside.Data BID")
                : fixture.replace("<b:Data i:bid", "<b:Data xmlns:b=\"http://www.interlis.ch/xtf/2.4/DbOutside\" i:bid");
        var invalid = Files.writeString(work.resolve("mixed-outside.xtf"), outside);
        var tail = Files.writeString(work.resolve("mixed-tail.xtf"), fixture.replace("mixed-", "tail-"));
        var output = work.resolve("mixed-import-output.xtf");
        for (var db : List.of(baseline, nativeSchema)) {
            String a = tableFor(db, warehouse + ".Data.Entry");
            String b = tableFor(db, workshop + ".Data.Entry");
            try (var sql = db.connect()) {
                var failure = cli(db, "import", warehouse, "native", invalid, CancellationToken.none(), "--model", workshop);
                assertEquals(1, failure.code(), failure::diagnostics);
                assertTrue(failure.diagnostics().contains("DbOutside"), failure::diagnostics);
                for (var table : List.of(a, b, "t_ili2db_dataset", "t_ili2db_basket")) {
                    assertEquals("0", scalar(sql, "SELECT COUNT(*) FROM " + db.table(table)), table);
                }
                var imported = cli(db, "import", warehouse, "native", input, CancellationToken.none(), "--model", workshop);
                assertEquals(0, imported.code(), imported::diagnostics);
                assertEquals("mixed-warehouse-entry", scalar(sql, "SELECT t_ili_tid FROM " + db.table(a)));
                assertEquals("mixed-workshop-entry", scalar(sql, "SELECT t_ili_tid FROM " + db.table(b)));
                referenceExport(db, "native", output);
                assertMixedDelivery(output, warehouse, workshop, "mixed", "warehouse");
                referenceImport(db, "tail", tail);
                var exported = cli(db, "export", warehouse, "tail", output, CancellationToken.none(), "--model", workshop);
                assertEquals(0, exported.code(), exported::diagnostics);
                assertMixedDelivery(output, warehouse, workshop, "tail", "warehouse");
                String ids = List.of(a, b, "t_ili2db_dataset", "t_ili2db_basket").stream()
                        .map(table -> "SELECT t_id FROM " + db.table(table)).collect(java.util.stream.Collectors.joining(" UNION ALL "));
                assertEquals("10", scalar(sql, "SELECT COUNT(DISTINCT t_id) FROM (" + ids + ") ids"));
            }
        }
    }

    void mixedMutation(TestDatabase db) throws Exception {
        String warehouse = "DbWarehouse" + version;
        String workshop = "DbWorkshop" + version;
        String incoming = "DbIncoming" + version;
        for (String model : List.of(warehouse, workshop, incoming)) fixture(model + ".ili");
        createReference(db, String.join(";", warehouse, workshop, incoming));
        var input = fixture("DbMixedModels" + version + ".xtf");
        referenceImport(db, "mixed", input);
        referenceImport(db, "incoming", fixture(incoming + ".xtf"));
        var single = fixture(warehouse + ".xtf");
        referenceImport(db, "independent", single);
        String contents = Files.readString(single).replace("fixture-", "mixed-");
        var update = Files.writeString(work.resolve("update-warehouse.xtf"), contents.replace(">warehouse<", ">updated<"));
        var wrongTopic = Files.writeString(work.resolve("wrong-topic.xtf"),
                contents.replace("mixed-warehouse-basket", "mixed-workshop-basket"));
        var removeTarget = Files.writeString(work.resolve("remove-target.xtf"), contents.lines()
                .filter(line -> !line.contains("mixed-warehouse-entry")).collect(java.util.stream.Collectors.joining("\n")));
        String a = tableFor(db, warehouse + ".Data.Entry");
        String b = tableFor(db, workshop + ".Data.Entry");
        String aId = "SELECT t_id FROM " + db.table(a) + " WHERE t_ili_tid='mixed-warehouse-entry'";
        String bId = "SELECT t_id FROM " + db.table(b) + " WHERE t_ili_tid='mixed-workshop-entry'";
        String basketId = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='mixed-warehouse-basket'";
        String datasetId = "SELECT t_id FROM " + db.table("t_ili2db_dataset") + " WHERE datasetname='mixed'";
        var output = work.resolve("mixed-mutated.xtf");
        try (var sql = db.connect()) {
            String oldA = scalar(sql, aId);
            String link = tableFor(db, incoming + ".Survey.Link");
            assertEquals(oldA, scalar(sql, "SELECT destination FROM " + db.table(link)));
            assertEquals(a, scalar(sql, "SELECT setting FROM " + db.table("t_ili2db_column_prop")
                    + " WHERE tablename='" + link + "' AND columnname='destination' AND tag='ch.ehi.ili2db.foreignKey'"));
            String oldB = scalar(sql, bId);
            String oldBasket = scalar(sql, basketId);
            String oldDataset = scalar(sql, datasetId);
            var updated = cli(db, "update", warehouse, "mixed", update, CancellationToken.none());
            assertEquals(0, updated.code(), updated::diagnostics);
            assertEquals(oldA, scalar(sql, aId));
            assertEquals(oldB, scalar(sql, bId));
            assertEquals(oldBasket, scalar(sql, basketId));
            referenceExport(db, "mixed", output);
            assertMixedDelivery(output, warehouse, workshop, "mixed", "updated");

            assertCliFailure(db, "update", warehouse, "mixed", wrongTopic, "another dataset or topic");
            assertCliFailure(db, "update", warehouse, "mixed", removeTarget, "referenced object");
            assertCliFailure(db, "replace", warehouse, "mixed", update, "outside the selected model");
            assertCliFailure(db, "delete", warehouse, "mixed", null, "outside the selected model");
            assertCliFailure(db, "replace", warehouse, "mixed", input, "referenced object", "--model", workshop);
            assertCliFailure(db, "delete", warehouse, "mixed", null, "referenced object", "--model", workshop);
            assertEquals(oldA, scalar(sql, aId));
            assertEquals(oldB, scalar(sql, bId));
            assertEquals(oldBasket, scalar(sql, basketId));
            referenceExport(db, "mixed", output);
            assertMixedDelivery(output, warehouse, workshop, "mixed", "updated");

            assertCliSuccess(db, "delete", incoming, "incoming", null);
            var replaced = cli(db, "replace", warehouse, "mixed", input, CancellationToken.none(), "--model", workshop);
            assertEquals(0, replaced.code(), replaced::diagnostics);
            assertEquals(oldDataset, scalar(sql, datasetId));
            assertNotEquals(oldA, scalar(sql, aId));
            assertNotEquals(oldB, scalar(sql, bId));
            referenceExport(db, "mixed", output);
            assertMixedDelivery(output, warehouse, workshop, "mixed", "warehouse");
            var deleted = cli(db, "delete", warehouse, "mixed", null, CancellationToken.none(), "--model", workshop);
            assertEquals(0, deleted.code(), deleted::diagnostics);
            assertEquals("independent", scalar(sql, "SELECT datasetname FROM " + db.table("t_ili2db_dataset")));
            referenceExport(db, "independent", output);
            assertEntryDelivery(output, warehouse, "warehouse", "fixture");
        }
    }

    void appendModels(TestDatabase db) throws Exception {
        String original = "DbImportedStruct" + version;
        String added = "DbLaterStruct" + version;
        String library = "DbSharedDetail" + version;
        var existingFile = fixture(original + ".ili");
        var sharedFile = fixture(library + ".ili");
        var newFile = Files.writeString(work.resolve(added + ".ili"), Files.readString(existingFile).replace(original, added));
        var oldTransfer = fixture(original + ".xtf");
        createReference(db, original, existingFile);
        referenceImport(db, "original", oldTransfer);
        var badLibrary = Files.writeString(work.resolve("conflicting-library.ili"),
                Files.readString(sharedFile).replace("TEXT*20", "TEXT*21"));
        assertCliFailure(db, "schemaimport", added, null, newFile, "Stored MODEL source differs for " + library,
                "--add-models", "--model-file", badLibrary.toString());
        assertCliFailure(db, "schemaimport", added, null, newFile, "conflict with the existing ili2db SETTINGS",
                "--add-models", "--model-file", sharedFile.toString(), "--default-epsg", "21781");

        try (var sql = db.connect()) {
            var before = DbSchemaCatalog.read(sql, db.postgis(), db.schema());
            String oldId = scalar(sql, "SELECT t_id FROM " + db.table("report"));
            var checks = new AtomicInteger();
            var walker = StackWalker.getInstance();
            CancellationToken duringDdl = () -> walker.walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals(SchemaSql.class.getName()) && frame.getMethodName().equals("apply")))
                    && checks.incrementAndGet() == 2;
            var cancelled = schemaWithIdentityColumns(db, added, newFile, duringDdl, "--add-models",
                    "--model-file", sharedFile.toString());
            assertEquals(130, cancelled.code(), cancelled::diagnostics);
            assertEquals(2, checks.get());
            before.assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
            assertEquals(oldId, scalar(sql, "SELECT t_id FROM " + db.table("report")));

            var extended = schemaWithIdentityColumns(db, added, newFile, CancellationToken.none(), "--add-models",
                    "--model-file", sharedFile.toString());
            assertEquals(0, extended.code(), extended::diagnostics);
            assertEquals(before.relations().get("settings"), DbSchemaCatalog.read(sql, db.postgis(), db.schema())
                    .relations().get("settings"));
            assertEquals(oldId, scalar(sql, "SELECT t_id FROM " + db.table("report")));
            assertEquals("report", tableFor(db, original + ".Data.Report"));
            String newTable = tableFor(db, added + ".Data.Report");
            assertEquals("dblaterstrct" + version + "data_report", newTable);
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_model")));
            assertEquals("1", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_classname")
                    + " WHERE iliname='" + library + ".Detail'"));

            for (var file : List.of(existingFile, sharedFile, newFile, badLibrary)) Files.delete(file);
            var output = work.resolve("append-output.xtf");
            assertCliSuccess(db, "export", original, "original", output);
            assertStructures(output, version, original, library, "fixture");
            String addition = Files.readString(oldTransfer).replace(original, added);
            var addInput = Files.writeString(work.resolve("new-model.xtf"), addition.replace("fixture-", "native-"));
            assertCliSuccess(db, "import", added, "native", addInput);
            referenceExport(db, "native", output);
            assertStructures(output, version, added, library, "native");

            var tail = Files.writeString(work.resolve("new-model-tail.xtf"), addition.replace("fixture-", "tail-"));
            referenceImport(db, "tail", tail);
            assertCliSuccess(db, "export", added, "tail", output);
            assertStructures(output, version, added, library, "tail");
            String ids = List.of("report", newTable, "detail", "t_ili2db_dataset", "t_ili2db_basket").stream()
                    .map(table -> "SELECT t_id FROM " + db.table(table)).collect(java.util.stream.Collectors.joining(" UNION ALL "));
            assertEquals("24", scalar(sql, "SELECT COUNT(DISTINCT t_id) FROM (" + ids + ") ids"));
        }
    }

    private void assertMixedDelivery(Path output, String warehouse, String workshop, String prefix, String warehouseMemo)
            throws Exception {
        var data = readDataSection(output, version);
        assertEquals(Set.of(warehouse, workshop), Set.copyOf(headerModels(data)));
        var baskets = children(data);
        assertEquals(2, baskets.size());
        for (var basket : baskets) {
            String bid = id(basket, version, "bid");
            if (bid.equals(prefix + "-warehouse-basket")) assertEntry(basket, warehouse, "warehouse", prefix, warehouseMemo);
            else assertEntry(basket, workshop, "workshop", prefix, "workshop");
        }
        assertEquals(Set.of(prefix + "-warehouse-basket", prefix + "-workshop-basket"),
                Set.copyOf(baskets.stream().map(basket -> id(basket, version, "bid")).toList()));
    }

    private void assertEntryDelivery(Path output, String model, String value, String prefix) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(model), headerModels(data));
        assertEntry(children(data).getFirst(), model, value, prefix, value);
    }

    private void assertEntry(Element basket, String model, String value, String prefix, String memo) {
        assertEquals(version == 23 ? model + ".Data" : "Data", basket.getLocalName());
        if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + model, basket.getNamespaceURI());
        assertEquals(prefix + "-" + value + "-basket", id(basket, version, "bid"));
        var entry = children(basket).getFirst();
        assertEquals(prefix + "-" + value + "-entry", id(entry, version, "tid"));
        assertEquals(memo, child(entry, "Memo").getTextContent());
    }

    private static String tableFor(TestDatabase db, String iliName) throws Exception {
        try (var sql = db.connect()) {
            return scalar(sql, "SELECT sqlname FROM " + db.table("t_ili2db_classname") + " WHERE iliname='" + iliName + "'");
        }
    }

    private List<String> headerModels(Element data) {
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        return children(child(header, version == 23 ? "MODELS" : "models")).stream()
                .map(model -> version == 23 ? model.getAttribute("NAME") : model.getTextContent().strip()).toList();
    }

    private Path fixture(String name) throws Exception {
        Path target = work.resolve(name);
        try (var resource = ModelCases.class.getResourceAsStream("/" + name)) {
            Files.copy(resource, target);
        }
        return target;
    }

    private void createReference(TestDatabase db, String models, Path... sources) throws Exception {
        var args = new ArrayList<>(List.of("--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056", "--models", models));
        for (var source : sources) args.add(source.toString());
        reference.run(db, work.toString(), args.toArray(String[]::new));
    }

    private void referenceImport(TestDatabase db, String dataset, Path file) throws Exception {
        reference.run(db, STORED_MODELS, "--import", "--dataset", dataset, "--importBid", "--importTid", file.toString());
    }

    private void referenceExport(TestDatabase db, String dataset, Path file) throws Exception {
        reference.run(db, STORED_MODELS, "--export", "--dataset", dataset, "--exportTid", file.toString());
    }
}
