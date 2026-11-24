package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static guru.interlis.db.DbTestSupport.schemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.assertCliSuccess;
import static guru.interlis.db.DbTestSupport.assertMissingTarget;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.execute;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AlternateCrsCases {
    private final Path work;
    private final ReferenceTool reference;

    AlternateCrsCases(Path work, ReferenceTool reference) {
        this.work = work;
        this.reference = reference;
    }

    void export(TestDatabase db) throws Exception {
        createReference(db, modelFile());
        Path input = delivery("legacy");
        reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importBid", "--importTid", input.toString());
        Path baseline = work.resolve("alt-ref.xtf");
        reference.run(db, work.toString(), "--export", "--dataset", "legacy", "--exportTid", baseline.toString());
        assertDelivery(baseline, "legacy");
        Path output = work.resolve("alt-own.xtf");
        assertCliSuccess(db, "export", "Current23", "legacy", output);
        assertDelivery(output, "legacy");
        String saved = Files.readString(output);
        try (var sql = db.connect()) {
            execute(sql, "UPDATE " + db.table("t_ili2db_basket")
                    + " SET topic='Unknown23.Data' WHERE t_ili_tid='basket-old-legacy'");
        }
        var result = cli(db, "export", "Current23", "legacy", output, CancellationToken.none());
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("outside the selected model"), result::diagnostics);
        assertEquals(saved, Files.readString(output));
    }

    void mutate(TestDatabase db) throws Exception {
        createOwn(db, modelFile(), "--create-geom-index");
        Path initial = delivery("main");
        assertCliSuccess(db, "import", "Current23", "main", initial);
        reference.run(db, work.toString(), "--import", "--dataset", "other", "--importTid", "--importBid",
                delivery("other").toString());

        String start = Files.readString(initial);
        Path updated = Files.writeString(work.resolve("alternate-update.xtf"), start.replace("600100.125", "610000.125"));
        String replacement = start.replace("Old23.Data", "Current23.Data")
                .replace("600100.125", "2620000.125").replace("200200.375", "1220000.375");
        Path switched = Files.writeString(work.resolve("alternate-switch.xtf"), replacement);
        Path invalid = Files.writeString(work.resolve("alternate-replace-invalid.xtf"),
                replacement.replace("<C1>2620000.125</C1>", "<C1>invalid</C1>"));
        String object = "SELECT t_id FROM " + db.table("station") + " WHERE t_ili_tid='old-main'";
        String basket = "SELECT t_id FROM " + db.table("t_ili2db_basket") + " WHERE t_ili_tid='basket-old-main'";
        String oldTable = db.postgis() ? "station" : "station_location_21781";
        try (var sql = db.connect()) {
            String oldObject = scalar(sql, object);
            String oldBasket = scalar(sql, basket);
            assertCliSuccess(db, "update", "Current23", "main", updated);
            assertEquals(oldObject, scalar(sql, object));
            assertEquals(oldBasket, scalar(sql, basket));
            CrsFixtures.assertHits(db, sql, oldTable, "location_21781", "610000.125", "200200.375", 21781, 1);
            Path output = work.resolve("alternate-updated.xtf");
            reference.run(db, work.toString(), "--export", "--dataset", "main", "--exportTid", output.toString());
            assertEquals("Old23.Data", basket(output, "basket-old-main").getLocalName());
            assertCoordinate(basket(output, "basket-old-main"), "610000.125", "200200.375");

            var denied = cli(db, "update", "Current23", "main", switched, CancellationToken.none());
            assertNotEquals(0, denied.code(), denied::diagnostics);
            assertTrue(denied.diagnostics().contains("another dataset or topic"), denied::diagnostics);
            var rollback = cli(db, "replace", "Current23", "main", invalid, CancellationToken.none());
            assertNotEquals(0, rollback.code(), rollback::diagnostics);
            assertEquals(oldObject, scalar(sql, object));
            assertEquals(oldBasket, scalar(sql, basket));
            CrsFixtures.assertHits(db, sql, oldTable, "location_21781", "610000.125", "200200.375", 21781, 1);
            CrsFixtures.assertHits(db, sql, "station", "location_2056", "2620000.125", "1220000.375", 2056, 0);

            assertCliSuccess(db, "replace", "Current23", "main", switched);
            assertNotEquals(oldObject, scalar(sql, object));
            CrsFixtures.assertHits(db, sql, oldTable, "location_21781", "610000.125", "200200.375", 21781, 0);
            CrsFixtures.assertHits(db, sql, "station", "location_2056", "2620000.125", "1220000.375", 2056, 1);
            output = work.resolve("alternate-replaced.xtf");
            reference.run(db, work.toString(), "--export", "--dataset", "main", "--exportTid", output.toString());
            assertEquals("Current23.Data", basket(output, "basket-old-main").getLocalName());
            assertCoordinate(basket(output, "basket-old-main"), "2620000.125", "1220000.375");

            assertCliSuccess(db, "delete", "Current23", "main", null);
            assertEquals("1", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_dataset")));
            assertEquals("2", scalar(sql, "SELECT count(*) FROM " + db.table("t_ili2db_basket")));
            CrsFixtures.assertHits(db, sql, "station", "location_2056", "2620000.125", "1220000.375", 2056, 0);
            CrsFixtures.assertHits(db, sql, "station", "location_2056", "2600001.625", "1200002.125", 2056, 1);
            CrsFixtures.assertHits(db, sql, oldTable, "location_21781", "600100.125", "200200.375", 21781, 1);
        }
    }

    private static org.w3c.dom.Element basket(Path output, String bid) throws Exception {
        return children(readDataSection(output, 23)).stream().filter(b -> bid.equals(id(b, 23, "bid"))).findFirst().orElseThrow();
    }

    private static void assertCoordinate(org.w3c.dom.Element basket, String x, String y) {
        var coord = child(child(children(basket).getFirst(), "Location"), "COORD");
        assertEquals(0, new BigDecimal(x).compareTo(new BigDecimal(child(coord, "C1").getTextContent())));
        assertEquals(0, new BigDecimal(y).compareTo(new BigDecimal(child(coord, "C2").getTextContent())));
    }

    void exchange(TestDatabase legacy, TestDatabase own, TestDatabase rejected) throws Exception {
        Path model = modelFile();
        createReference(legacy, model);
        createOwn(own, model);
        try (var oldSql = legacy.connect(); var ownSql = own.connect()) {
            DbSchemaCatalog.read(oldSql, legacy.postgis(), legacy.schema())
                    .assertSameAs(DbSchemaCatalog.read(ownSql, own.postgis(), own.schema()));
        }
        for (var db : List.of(legacy, own)) {
            reference.run(db, work.toString(), "--import", "--dataset", "legacy", "--importBid", "--importTid",
                    delivery("legacy").toString());
            var nativeExport = work.resolve(db.schema() + "-native.xtf");
            assertCliSuccess(db, "export", "Current23", "legacy", nativeExport);
            assertDelivery(nativeExport, "legacy");

            assertCliSuccess(db, "import", "Current23", "native", delivery("native"));
            var referenceExport = work.resolve(db.schema() + "-reference.xtf");
            reference.run(db, work.toString(), "--export", "--dataset", "native", "--exportTid", referenceExport.toString());
            assertDelivery(referenceExport, "native");
            try (var sql = db.connect()) {
                String basket = db.table("t_ili2db_basket");
                String station = db.table("station");
                assertEquals("Old23.Data", scalar(sql, "SELECT topic FROM " + basket + " WHERE t_ili_tid='basket-old-native'"));
                assertEquals("", scalar(sql, "SELECT domains FROM " + basket + " WHERE t_ili_tid='basket-old-native'"));
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + station
                        + " WHERE t_ili_tid='old-native' AND location_2056 IS NULL"));
                String current = db.postgis() ? "ST_SRID(location_2056)=2056" : "hex(substr(location_2056,5,4))='00000808'";
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + station + " WHERE t_ili_tid='current-native' AND " + current));
                String old = db.postgis() ? station : db.table("station_location_21781");
                String hasOld = db.postgis() ? "ST_SRID(location_21781)=21781" : "hex(substr(location_21781,5,4))='00005515'";
                assertEquals("1", scalar(sql, "SELECT count(*) FROM " + old + " WHERE t_id=(SELECT t_id FROM " + station
                        + " WHERE t_ili_tid='old-native') AND " + hasOld));
            }
        }

        String content = Files.readString(model);
        int alternate = content.indexOf("MODEL Old23");
        Path invalidModel = Files.writeString(work.resolve("Incompatible.ili"), content.substring(0, alternate)
                + content.substring(alternate).replace("TEXT*32", "TEXT*16"));
        var result = schemaWithIdentityColumns(rejected, "Current23", invalidModel, CancellationToken.none(),
                "--multi-srs", "--alt-srs-model", "Current23=Old23");
        assertNotEquals(0, result.code(), result::diagnostics);
        assertTrue(result.diagnostics().contains("structurally incompatible"), result::diagnostics);
        assertMissingTarget(rejected);
    }

    private static void createOwn(TestDatabase db, Path model, String... extra) {
        var options = new java.util.ArrayList<>(List.of("--multi-srs", "--alt-srs-model", "Current23=Old23"));
        options.addAll(List.of(extra));
        var result = schemaWithIdentityColumns(db, "Current23", model, CancellationToken.none(), options.toArray(String[]::new));
        assertEquals(0, result.code(), result::diagnostics);
    }

    private Path modelFile() throws Exception {
        Path path = work.resolve("AlternateCrs23.ili");
        try (var resource = getClass().getResourceAsStream("/AlternateCrs23.ili")) {
            Files.copy(resource, path);
        }
        return path;
    }

    private void createReference(TestDatabase db, Path model) throws Exception {
        reference.run(db, work.toString(), "--schemaimport", "--noSmartMapping", "--createBasketCol", "--createTidCol",
                "--sqlEnableNull", "--defaultSrsAuth", "EPSG", "--defaultSrsCode", "2056",
                "--models", "Current23", "--multiSrs", "--altSrsModel", "Current23=Old23", model.toString());
    }

    private Path delivery(String suffix) throws Exception {
        return Files.writeString(work.resolve("alternate-" + suffix + ".xtf"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="interop"><MODELS>
                    <MODEL NAME="Current23" VERSION="1" URI="https://example.org/interlis/db"/>
                    <MODEL NAME="Old23" VERSION="1" URI="https://example.org/interlis/db"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION>
                    <Current23.Data BID="basket-current-%1$s"><Current23.Data.Station TID="current-%1$s">
                      <Name>Current</Name><Location><COORD><C1>2600001.625</C1><C2>1200002.125</C2></COORD></Location>
                    </Current23.Data.Station></Current23.Data>
                    <Old23.Data BID="basket-old-%1$s"><Old23.Data.Station TID="old-%1$s">
                      <Name>Old</Name><Location><COORD><C1>600100.125</C1><C2>200200.375</C2></COORD></Location>
                    </Old23.Data.Station></Old23.Data>
                  </DATASECTION>
                </TRANSFER>
                """.formatted(suffix));
    }

    private static void assertDelivery(Path output, String suffix) throws Exception {
        var section = readDataSection(output, 23);
        var header = child((org.w3c.dom.Element) section.getParentNode(), "HEADERSECTION");
        var names = children(child(header, "MODELS")).stream().map(element -> element.getAttribute("NAME")).toList();
        assertTrue(names.containsAll(List.of("Current23", "Old23")), names.toString());
        var baskets = children(section);
        assertEquals(2, baskets.size());
        for (String model : List.of("Current23", "Old23")) {
            boolean current = model.equals("Current23");
            String tid = (current ? "current-" : "old-") + suffix;
            var basket = baskets.stream().filter(b -> ("basket-" + tid).equals(id(b, 23, "bid"))).findFirst().orElseThrow();
            assertEquals(model + ".Data", basket.getLocalName());
            var object = children(basket).getFirst();
            assertEquals(model + ".Data.Station", object.getLocalName());
            assertEquals(tid, id(object, 23, "tid"));
            assertEquals(current ? "Current" : "Old", child(object, "Name").getTextContent());
            assertCoordinate(basket, current ? "2600001.625" : "600100.125", current ? "1200002.125" : "200200.375");
        }
    }
}
