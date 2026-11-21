package guru.interlis.db;

import guru.interlis.db.DbInteropTest.ReferenceTool;
import guru.interlis.db.DbInteropTest.TestDatabase;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbInteropTest.assertCliSuccess;
import static guru.interlis.db.DbInteropTest.child;
import static guru.interlis.db.DbInteropTest.children;
import static guru.interlis.db.DbInteropTest.cli;
import static guru.interlis.db.DbInteropTest.execute;
import static guru.interlis.db.DbInteropTest.id;
import static guru.interlis.db.DbInteropTest.readDataSection;
import static guru.interlis.db.DbInteropTest.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    void sharedSource(TestDatabase db) throws Exception {
        String model = "DbSharedSource" + version;
        Path source = fixture(model + ".ili");
        createReference(db, model, source);
        Files.delete(source);
        try (var sql = db.connect()) {
            assertEquals("DbSourceTypes" + version + " " + model + "{ DbSourceTypes" + version + "}",
                    scalar(sql, "SELECT modelname FROM " + db.table("t_ili2db_model")));
            assertEquals(model + ".Data.Station", scalar(sql, "SELECT iliname FROM " + db.table("t_ili2db_classname")));
        }
        exchangeDomains(db, model, fixture(model + ".xtf"));
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_basket") + " SET topic='DbSourceTypes" + version
                    + ".Unused' WHERE t_ili_tid='fixture-basket'");
            var result = cli(db, "delete", model, "legacy", null, CancellationToken.none());
            assertEquals(1, result.code(), result::diagnostics);
            assertTrue(result.diagnostics().contains("outside the selected model"), result::diagnostics);
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station")));
        }
    }

    void storedImports(TestDatabase db) throws Exception {
        String model = "DbStoredImports" + version;
        String types = "DbStoredTypes" + version;
        var main = fixture(model + ".ili");
        var middle = fixture("DbStoredMiddle" + version + ".ili");
        var leaf = fixture(types + ".ili");
        createReference(db, model, main);
        for (var source : List.of(main, middle, leaf)) Files.delete(source);
        exchangeDomains(db, model, domainInput(model));
        var output = work.resolve("legacy-output.xtf");
        String published = Files.readString(output);
        String catalog = db.table("t_ili2db_model");
        try (var sql = db.connect()) {
            assertEquals("3", scalar(sql, "SELECT COUNT(*) FROM " + catalog));
            execute(sql, "INSERT INTO " + catalog + " (filename,iliversion,modelname,content,importdate)"
                    + " SELECT filename,iliversion,modelname||' DbDuplicate',content,importdate FROM " + catalog
                    + " WHERE modelname='" + types + "'");
            var ambiguous = cli(db, "export", model, "legacy", output, CancellationToken.none());
            assertEquals(1, ambiguous.code(), ambiguous::diagnostics);
            assertTrue(ambiguous.diagnostics().contains("Multiple MODEL sources for " + types), ambiguous::diagnostics);
            assertEquals(published, Files.readString(output));
            execute(sql, "DELETE FROM " + catalog + " WHERE modelname LIKE '" + types + "%'");
            var missing = cli(db, "export", model, "legacy", output, CancellationToken.none());
            assertEquals(1, missing.code(), missing::diagnostics);
            assertTrue(missing.diagnostics().contains("No MODEL source for " + types), missing::diagnostics);
            assertEquals(published, Files.readString(output));
        }
    }

    void localImports(TestDatabase baseline, TestDatabase db) throws Exception {
        String model = "DbStoredImports" + version;
        var main = fixture(model + ".ili");
        var middle = fixture("DbStoredMiddle" + version + ".ili");
        var leaf = fixture("DbStoredTypes" + version + ".ili");
        createReference(baseline, model, main);
        var missing = cli(db, "schemaimport", model, null, main, CancellationToken.none(), "--model-file", middle.toString());
        assertEquals(1, missing.code(), missing::diagnostics);
        assertTrue(missing.diagnostics().contains("DbStoredTypes" + version), missing::diagnostics);
        var created = cli(db, "schemaimport", model, null, main, CancellationToken.none(),
                "--model-file", middle.toString(), "--model-file", leaf.toString());
        assertEquals(0, created.code(), created::diagnostics);
        for (var source : List.of(main, middle, leaf)) Files.delete(source);
        try (var refSql = baseline.connect(); var sql = db.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, db.postgis(), db.schema()));
        }
        exchangeDomains(db, model, domainInput(model));
    }

    void importedStructures(TestDatabase baseline, TestDatabase nativeSchema) throws Exception {
        String model = "DbImportedStruct" + version;
        String library = "DbSharedDetail" + version;
        var source = fixture(model + ".ili");
        var types = fixture(library + ".ili");
        createReference(baseline, model, source);
        var result = cli(nativeSchema, "schemaimport", model, null, source, CancellationToken.none(),
                "--model-file", types.toString());
        assertEquals(0, result.code(), result::diagnostics);
        try (var refSql = baseline.connect(); var sql = nativeSchema.connect()) {
            DbSchemaCatalog.read(refSql, baseline.postgis(), baseline.schema())
                    .assertSameAs(DbSchemaCatalog.read(sql, nativeSchema.postgis(), nativeSchema.schema()));
        }
        Files.delete(source);
        Files.delete(types);
        var input = fixture(model + ".xtf");
        var second = Files.writeString(work.resolve("own-detail.xtf"), Files.readString(input).replace("fixture-", "own-"));
        for (var db : List.of(baseline, nativeSchema)) {
            referenceImport(db, "legacy", input);
            Path output = work.resolve("legacy-detail.xtf");
            assertCliSuccess(db, "export", model, "legacy", output);
            assertStructures(output, model, library, "fixture");
            assertCliSuccess(db, "import", model, "native", second);
            referenceExport(db, "native", output);
            assertStructures(output, model, library, "own");
            try (var sql = db.connect()) {
                assertEquals(library + ".Detail", scalar(sql, "SELECT iliname FROM " + db.table("t_ili2db_classname")
                        + " WHERE sqlname='detail'"));
                assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("t_ili2db_classname")));
                for (var field : List.of("summary", "entries", "observations")) {
                    String query = "SELECT COUNT(*) FROM " + db.table("detail") + " d JOIN "
                            + db.table("report") + " r ON d.report_" + field + "=r.t_id AND d.t_basket=r.t_basket";
                    int expected = field.equals("summary") ? 2 : 4;
                    assertEquals(Integer.toString(expected), scalar(sql, query));
                    assertEquals("2", scalar(sql, query + " WHERE d.t_seq=0"));
                }
            }
        }
    }

    private void assertStructures(Path file, String model, String library, String prefix) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(model), headerModels(data));
        var basket = children(data).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var report = children(basket).getFirst();
        assertEquals(prefix + "-report", id(report, version, "tid"));
        assertEquals(List.of("summary"), structureLabels(report, "Summary", library));
        assertEquals(List.of("second", "first"), structureLabels(report, "Entries", library));
        assertEquals(List.of("same", "same"), structureLabels(report, "Observations", library));
    }

    private List<String> structureLabels(Element parent, String name, String library) {
        return children(parent).stream().filter(field -> name.equals(field.getLocalName()))
                .flatMap(field -> children(field).stream()).map(detail -> {
                    assertEquals(version == 23 ? library + ".Detail" : "Detail", detail.getLocalName());
                    if (version == 24) assertEquals("http://www.interlis.ch/xtf/2.4/" + library, detail.getNamespaceURI());
                    return child(detail, "Label").getTextContent();
                }).toList();
    }

    void importedTopics(TestDatabase baseline, TestDatabase nativeSchema) throws Exception {
        String model = "DbReachTopic" + version;
        String library = "DbImportedTarget" + version;
        var modelFile = fixture(model + ".ili");
        var libraryFile = fixture(library + ".ili");
        createReference(baseline, model, modelFile);
        var schema = cli(nativeSchema, "schemaimport", model, null, modelFile, CancellationToken.none(),
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
        var created = cli(nativeSchema, "schemaimport", warehouse, null, first, CancellationToken.none(),
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
        var created = cli(nativeSchema, "schemaimport", warehouse, null, first, CancellationToken.none(),
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

    private Path domainInput(String model) throws Exception {
        String shared = "DbSharedSource" + version;
        var input = work.resolve(model + ".xtf");
        Files.writeString(input, Files.readString(fixture(shared + ".xtf")).replace(shared, model));
        return input;
    }

    private void exchangeDomains(TestDatabase db, String model, Path input) throws Exception {
        referenceImport(db, "legacy", input);
        Path output = work.resolve("legacy-output.xtf");
        assertCliSuccess(db, "export", model, "legacy", output);
        assertDomainDelivery(output, model, "fixture");

        Path second = work.resolve("own-input.xtf");
        Files.writeString(second, Files.readString(input).replace("fixture-", "own-"));
        assertCliSuccess(db, "import", model, "own", second);
        output = work.resolve("own-output.xtf");
        referenceExport(db, "own", output);
        assertDomainDelivery(output, model, "own");
        try (var sql = db.connect()) {
            String query = db.postgis()
                    ? "SELECT DISTINCT ST_SRID(location) FROM " + db.table("station")
                    : "SELECT srs_id FROM gpkg_geometry_columns WHERE table_name='station' AND column_name='location'";
            assertEquals("2056", scalar(sql, query));
            assertEquals("2", scalar(sql, "SELECT COUNT(*) FROM " + db.table("station")
                    + " WHERE aname='Imported domains' AND astate='active'"));
        }
    }

    private void assertDomainDelivery(Path file, String model, String prefix) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(model), headerModels(data));
        var basket = children(data).getFirst();
        assertEquals(prefix + "-basket", id(basket, version, "bid"));
        var station = children(basket).getFirst();
        assertEquals(prefix + "-station", id(station, version, "tid"));
        assertEquals("Imported domains", child(station, "Name").getTextContent());
        assertEquals("active", child(station, "State").getTextContent());
        var point = children(child(station, "Location")).getFirst();
        assertDecimal("2600123.125", children(point).get(0).getTextContent());
        assertDecimal("1200456.375", children(point).get(1).getTextContent());
    }

    private List<String> headerModels(Element data) {
        var header = child((Element) data.getParentNode(), version == 23 ? "HEADERSECTION" : "headersection");
        return children(child(header, version == 23 ? "MODELS" : "models")).stream()
                .map(model -> version == 23 ? model.getAttribute("NAME") : model.getTextContent().strip()).toList();
    }

    private static void assertDecimal(String expected, String actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual)));
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
